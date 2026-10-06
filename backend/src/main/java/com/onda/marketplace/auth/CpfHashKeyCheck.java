package com.onda.marketplace.auth;

import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.provider.ProviderCpfBackfill;
import com.onda.marketplace.provider.ProviderProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Recusa subir se houver conta com hash de CPF de uma versão de chave que a configuração não sabe mais calcular, OU se
 * a chave configurada para uma versão já gravada não é mais a mesma que calculou esses hashes.
 *
 * <p>Sem a 1ª conferência, esquecer {@code CPF_HASH_KEY_PREVIOUS} na primeira subida depois da separação das chaves não
 * dá erro nenhum: a unicidade deixa de enxergar essas contas, e o cliente que já confirmou o CPF fica sem conseguir
 * confirmar de novo (o hash dele não confere com nenhuma chave configurada).
 *
 * <p>Achado da revisão cruzada (2026-10-05): a conferência de VERSÃO não bastava — trocar o VALOR de
 * {@code CPF_HASH_KEY} sem subir {@code CPF_HASH_KEY_VERSION}, ou informar um {@code CPF_HASH_KEY_PREVIOUS} errado
 * (mesma versão, chave diferente), subia sem erro nenhum: a restrição UNIQUE passava a comparar hashes calculados com
 * chaves diferentes, que nunca batem, e a unicidade do CPF se furava silenciosamente. Agora cada versão tem um
 * verificador gravado (ver {@link CpfHashService#verificadorChaveAtual}): a 1ª subida de uma versão o grava (cruzando
 * contra um CPF de verdade quando existe um, nunca às cegas), as seguintes conferem. Também recusa subir se alguma
 * conta tiver hash de uma versão MAIOR que a configurada (rollback: a aplicação não sabe recalculá-lo).
 *
 * <p>Subir e deixar o problema escondido é pior que não subir; num deploy em rolagem a versão anterior continua no ar.
 * Roda antes do {@code ProviderCpfBackfill}.
 */
@Component
@Order(1)
@SuppressWarnings("null")
public class CpfHashKeyCheck implements ApplicationRunner {

    private final UserRepository                    userRepository;
    private final CpfHashService                     cpfHashService;
    private final ProviderProfileRepository          profileRepository;
    private final CpfEncryptor                        cpfEncryptor;
    private final CpfHashKeyVerificacaoRepository    verificacaoRepository;
    /** Mesma flag do {@code ProviderCpfBackfill}: onde {@code providers_profile} (coluna geográfica) não existe — o H2
     *  dos testes de contexto —, não há como cruzar contra uma âncora real; trata como "nenhuma âncora" sem consultar. */
    private final boolean                            cruzamentoAtivo;

    public CpfHashKeyCheck(UserRepository userRepository, CpfHashService cpfHashService,
                            ProviderProfileRepository profileRepository, CpfEncryptor cpfEncryptor,
                            CpfHashKeyVerificacaoRepository verificacaoRepository,
                            @Value("${marketplace.cpf-backfill.enabled:true}") boolean cruzamentoAtivo) {
        this.userRepository        = userRepository;
        this.cpfHashService        = cpfHashService;
        this.profileRepository     = profileRepository;
        this.cpfEncryptor          = cpfEncryptor;
        this.verificacaoRepository = verificacaoRepository;
        this.cruzamentoAtivo       = cruzamentoAtivo;
    }

    @Override
    public void run(ApplicationArguments args) {
        verificar();
    }

    /** Separado de {@link #run} para o teste chamar sem montar argumentos. */
    void verificar() {
        int versaoAtual = cpfHashService.versaoAtual();
        // com a chave anterior configurada, a versão logo abaixo da atual ainda se reconhece; mais antiga que isso, não
        int menorVersaoReconhecida = cpfHashService.temChaveAnterior() ? versaoAtual - 1 : versaoAtual;
        long semChave = userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(menorVersaoReconhecida);
        if (semChave > 0) {
            throw new IllegalStateException(
                    semChave + " conta(s) têm o hash do CPF calculado com uma chave que não está configurada (versão atual: "
                            + versaoAtual + "). Configure CPF_HASH_KEY_PREVIOUS com o valor que a chave tinha na versão "
                            + (versaoAtual - 1) + " (na migração da separação, o valor antigo de CPF_ENCRYPTION_KEY).");
        }

        long versaoMaior = userRepository.countByCpfHashIsNotNullAndCpfHashVersaoGreaterThan(versaoAtual);
        if (versaoMaior > 0) {
            throw new IllegalStateException(
                    versaoMaior + " conta(s) têm o hash do CPF numa versão de chave MAIOR que a configurada (atual: "
                            + versaoAtual + ") — a aplicação não sabe recalcular esse hash. Confirme CPF_HASH_KEY_VERSION "
                            + "antes de subir (indício de rollback para uma versão anterior).");
        }

        conferirOuRegistrarVerificador(versaoAtual, cpfHashService.verificadorChaveAtual(), "CPF_HASH_KEY");
        if (cpfHashService.temChaveAnterior()) {
            conferirOuRegistrarVerificador(versaoAtual - 1, cpfHashService.verificadorChaveAnterior(), "CPF_HASH_KEY_PREVIOUS");
        }
    }

    private void conferirOuRegistrarVerificador(int versao, String verificadorCalculado, String variavel) {
        var gravado = verificacaoRepository.findById(versao);
        if (gravado.isPresent()) {
            if (!gravado.get().getVerificador().equals(verificadorCalculado)) {
                throw new IllegalStateException(
                        "A chave do hash do CPF da versão " + versao + " não é mais a mesma que calculou os hashes já "
                                + "gravados nessa versão — confira " + variavel + ".");
            }
            return;
        }
        // 1ª subida desta versão: antes de confiar na chave, cruza contra uma conta cujo CPF decifrado já prova o hash
        if (!cruzamentoConfereOuNaoHaOQueCruzar(versao)) {
            throw new IllegalStateException(
                    variavel + " não corresponde ao que os hashes já gravados na versão " + versao + " esperam.");
        }
        verificacaoRepository.save(new CpfHashKeyVerificacao(versao, verificadorCalculado));
    }

    /** {@code true}: a chave da versão bate com uma âncora real, OU não existe âncora decifrável (nada para cruzar). */
    private boolean cruzamentoConfereOuNaoHaOQueCruzar(int versao) {
        if (!cruzamentoAtivo) {
            return true;
        }
        for (ProviderCpfBackfill.PerfilSemHash perfil : profileRepository.comHashNaVersao(versao)) {
            String cpfDecifrado;
            try {
                cpfDecifrado = cpfEncryptor.decrypt(perfil.getCpfCifrado());
            } catch (RuntimeException e) {
                continue;   // não decifra (chave de cifra trocada, placeholder do seed): não serve de âncora
            }
            User user = userRepository.findById(perfil.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            return cpfHashService.confere(cpfDecifrado, user.getCpfHash(), user.getCpfHashVersao());
        }
        return true;   // nenhuma âncora decifrável: confia na 1ª subida desta versão (trust-on-first-use)
    }
}
