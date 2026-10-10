package com.onda.marketplace.provider;

import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.shared.Cpf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Prestadores cadastrados antes de o cadastro gravar o hash do CPF só têm o CPF cifrado ({@code providers_profile}). Na
 * subida, lê esse CPF, grava o hash e deixa a unicidade (uma pessoa = um CPF) valer para eles também. Também regrava, com
 * a chave atual, o hash de quem o tem numa versão de chave anterior (rotação do HMAC: o prestador é o único que se refaz
 * sozinho, porque o CPF dele existe cifrado).
 *
 * <p>Idempotente (só toca em quem não tem hash ou o tem numa chave antiga) e sem efeito colateral em quem já está certo. Cada prestador é uma
 * transação: um registro ruim — CPF que não decifra (o seed grava um placeholder), duplicata — não derruba os outros.
 * Duplicata (o mesmo CPF em duas contas) NÃO é resolvida aqui: a conta não ganha o hash e fica listada no log, só pelo id,
 * para decisão humana. O CPF nunca vai para o log. Desliga com {@code marketplace.cpf-backfill.enabled=false}.
 */
@Component
public class ProviderCpfBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderCpfBackfill.class);

    /** Só o necessário, sem carregar a entidade. */
    public interface PerfilSemHash {
        UUID getUserId();
        String getCpfCifrado();
    }

    /** {@code idsIlegiveis}: só os ids (o CPF nunca vai para o log); {@link #ilegiveis()} é a contagem. */
    public record Resultado(int vinculados, List<UUID> duplicados, List<UUID> idsIlegiveis) {
        public int ilegiveis() { return idsIlegiveis.size(); }
    }

    private enum Desfecho { VINCULADO, DUPLICADO, JA_TINHA }

    private final ProviderProfileRepository profileRepository;
    private final UserRepository            userRepository;
    private final CpfEncryptor              cpfEncryptor;
    private final CpfHashService            cpfHashService;
    private final TransactionTemplate       transacao;
    private final boolean                   ativo;

    public ProviderCpfBackfill(ProviderProfileRepository profileRepository,
                               UserRepository userRepository,
                               CpfEncryptor cpfEncryptor,
                               CpfHashService cpfHashService,
                               TransactionTemplate transacao,
                               @Value("${marketplace.cpf-backfill.enabled:true}") boolean ativo) {
        this.profileRepository = profileRepository;
        this.userRepository    = userRepository;
        this.cpfEncryptor      = cpfEncryptor;
        this.cpfHashService    = cpfHashService;
        this.transacao         = transacao;
        this.ativo             = ativo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ativo) {
            return;
        }
        Resultado r;
        try {
            r = preencher();
        } catch (RuntimeException e) {
            // Nunca derruba a subida: é uma correção de dados de conveniência e roda de novo no próximo arranque.
            log.warn("Hash do CPF dos prestadores: falhou ({}); tenta de novo na próxima subida.", e.getClass().getSimpleName());
            return;
        }
        if (r.vinculados() > 0 || !r.duplicados().isEmpty() || r.ilegiveis() > 0) {
            log.info("Hash do CPF dos prestadores: {} vinculado(s), {} ilegível(is), {} duplicado(s) {}",
                    r.vinculados(), r.ilegiveis(), r.duplicados().size(),
                    r.duplicados().isEmpty() ? "" : "— mesmo CPF em mais de uma conta, decidir à mão (ids): " + r.duplicados());
        }
        if (r.ilegiveis() > 0) {
            // Achado da revisão cruzada (2ª rodada): o CPF cifrado desses prestadores não decifra, então nem a unicidade nem a checagem de
            // duplicata os alcançam — e isso se repete em toda subida. Antes só a contagem saía no log; sem os ids ninguém sabia quem investigar.
            log.warn("Prestadores cujo CPF cifrado NÃO decifra (a unicidade do CPF não os alcança; conferir à mão; ids): {}", r.idsIlegiveis());
        }
    }

    public Resultado preencher() {
        int vinculados = 0;
        List<UUID> idsIlegiveis = new ArrayList<>();
        List<UUID> duplicados = new ArrayList<>();
        for (PerfilSemHash perfil : profileRepository.semHashDoCpf(cpfHashService.versaoAtual())) {
            String cpf;
            try {
                cpf = Cpf.soDigitos(cpfEncryptor.decrypt(perfil.getCpfCifrado()));
            } catch (RuntimeException e) {
                idsIlegiveis.add(perfil.getUserId());   // não decifra (chave trocada, placeholder do seed): nada a fazer por aqui
                continue;
            }
            switch (vincular(perfil.getUserId(), cpf)) {
                case VINCULADO -> vinculados++;
                case DUPLICADO -> {
                    duplicados.add(perfil.getUserId());
                    marcarCpfNaoConciliado(perfil.getUserId());
                }
                case JA_TINHA  -> { /* ganhou o hash no meio do caminho: nada a refazer */ }
            }
        }
        return new Resultado(vinculados, duplicados, idsIlegiveis);
    }

    private Desfecho vincular(UUID userId, String cpf) {
        try {
            return transacao.execute(status -> {
                // Com trava de linha, como PRIMEIRA leitura da conta (rodada 3): o save abaixo regrava a entidade inteira. Sem a trava, uma
                // exclusão de conta que commitasse entre esta leitura e o save era desfeita (e-mail, nome e excluido_em voltavam ao valor
                // antigo) e a conta excluída ainda ganhava um hash de CPF. Conta excluída não é vinculada: não há mais o que proteger nela.
                User user = userRepository.findByIdComTrava(userId).orElse(null);
                if (user == null || user.isExcluido()
                        || (user.getCpfHash() != null && user.getCpfHashVersao() == cpfHashService.versaoAtual())) {
                    return Desfecho.JA_TINHA;
                }
                // o mesmo CPF em OUTRA conta, sob qualquer chave: a própria conta (hash de chave antiga) não é duplicata
                if (userRepository.existsByCpfHashInAndIdNot(cpfHashService.hashesPossiveis(cpf), userId)) {
                    return Desfecho.DUPLICADO;
                }
                user.vincularCpf(cpfHashService.hash(cpf), cpfHashService.versaoAtual());
                userRepository.save(user);
                return Desfecho.VINCULADO;
            });
        } catch (DataIntegrityViolationException corrida) {
            return Desfecho.DUPLICADO;   // outra instância vinculou o mesmo CPF entre a consulta e a gravação
        }
    }

    /**
     * Achado da revisão cruzada (2026-10-05): o prestador legado sem hash continuava VERIFICADO e apto a propor — o
     * self-hire só compara IDs de conta, nunca enxerga que é a MESMA pessoa por trás de duas contas com o mesmo CPF.
     * Marca o perfil para o guard de verificação recusar operar (propor/aceitar) até o suporte resolver a duplicata.
     */
    private void marcarCpfNaoConciliado(UUID userId) {
        // Mesma ordem do ModerationService: a conta travada primeiro, o perfil depois — o save do perfil regrava a linha inteira e,
        // sem a trava, desfazia a anonimização de uma exclusão que commitasse no meio (rodada 3).
        transacao.executeWithoutResult(status -> {
            User user = userRepository.findByIdComTrava(userId).orElse(null);
            if (user == null || user.isExcluido()) {
                return;
            }
            profileRepository.findByUserId(userId).ifPresent(perfil -> {
                perfil.marcarCpfNaoConciliado();
                profileRepository.save(perfil);
            });
        });
    }
}
