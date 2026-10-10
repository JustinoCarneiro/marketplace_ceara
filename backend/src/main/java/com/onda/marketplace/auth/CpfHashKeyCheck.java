package com.onda.marketplace.auth;

import com.onda.marketplace.provider.CpfEncryptor;
import com.onda.marketplace.provider.ProviderCpfBackfill;
import com.onda.marketplace.provider.ProviderProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
 * <p>Sem âncora (decisão do dono, 2026-10-09): só prestador tem o CPF cifrado, então o hash de um cliente não se prova. Na 1ª
 * subida de uma versão que já tem contas mas nenhum prestador decifrável, uma chave digitada errada seria gravada como
 * referência sem nada que a desminta — e a unicidade do CPF deixaria de enxergar essas contas. Antes era só um aviso; agora a
 * subida é RECUSADA, a menos que o operador confirme a chave explicitamente com {@code CPF_HASH_KEY_CONFIRMED=true} (uma vez:
 * depois o verificador gravado passa a valer e a variável deve sair). A confirmação só vale para o caso "sem âncora" — nunca
 * vence uma contradição (âncora que não confere, verificador já gravado que difere). Sem nenhuma conta na versão (instalação
 * nova, rotação recém-feita) não há o que proteger e a subida segue sem pedir nada.
 *
 * <p>Subir e deixar o problema escondido é pior que não subir; num deploy em rolagem a versão anterior continua no ar.
 *
 * <p><b>Roda ANTES de o servidor web aceitar requisições</b> (rodada 3): como {@link SmartInitializingSingleton} ela executa no fim da criação
 * dos singletons, antes de {@code finishRefresh} abrir a porta. Como {@code ApplicationRunner} ela só rodava DEPOIS de o servidor já atender —
 * no log de subida de 2026-10-10 o {@code DispatcherServlet} atendeu uma requisição às 20:40:31,5, antes de o runner do backfill terminar
 * (20:40:32,7). Um cadastro nessa janela, com a {@code CPF_HASH_KEY} errada, gravaria hash com a chave errada e a checagem recusaria subir
 * DEPOIS, deixando esses hashes para trás (unicidade furada para essas contas). Também roda, por consequência, antes do {@code ProviderCpfBackfill}.
 */
@Component
@SuppressWarnings("null")
public class CpfHashKeyCheck implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CpfHashKeyCheck.class);

    /** O que o cruzamento contra uma conta real concluiu sobre a chave de uma versão. */
    private enum Cruzamento { CONFERE, NAO_CONFERE, SEM_ANCORA }

    private final UserRepository                    userRepository;
    private final CpfHashService                     cpfHashService;
    private final ProviderProfileRepository          profileRepository;
    private final CpfEncryptor                        cpfEncryptor;
    private final CpfHashKeyVerificacaoRepository    verificacaoRepository;
    /** Mesma flag do {@code ProviderCpfBackfill}: onde {@code providers_profile} (coluna geográfica) não existe — o H2
     *  dos testes de contexto —, não há como cruzar contra uma âncora real; trata como "nenhuma âncora" sem consultar. Isso NÃO
     *  desliga a recusa: com contas na versão e sem âncora a subida continua recusada (só {@link #chaveConfirmada} a libera). */
    private final boolean                            cruzamentoAtivo;
    /** {@code CPF_HASH_KEY_CONFIRMED}: o operador afirma que a chave configurada é a que calculou os hashes já gravados, para a
     *  1ª subida de uma versão sem âncora. Só afrouxa o caso "sem âncora" — ver o Javadoc da classe. */
    private final boolean                            chaveConfirmada;

    public CpfHashKeyCheck(UserRepository userRepository, CpfHashService cpfHashService,
                            ProviderProfileRepository profileRepository, CpfEncryptor cpfEncryptor,
                            CpfHashKeyVerificacaoRepository verificacaoRepository,
                            @Value("${marketplace.cpf-backfill.enabled:true}") boolean cruzamentoAtivo,
                            @Value("${cpf.hash-key-confirmed:false}") boolean chaveConfirmada) {
        this.userRepository        = userRepository;
        this.cpfHashService        = cpfHashService;
        this.profileRepository     = profileRepository;
        this.cpfEncryptor          = cpfEncryptor;
        this.verificacaoRepository = verificacaoRepository;
        this.cruzamentoAtivo       = cruzamentoAtivo;
        this.chaveConfirmada       = chaveConfirmada;
    }

    @Override
    public void afterSingletonsInstantiated() {
        verificar();
    }

    /** Público para os testes (E2E, em outro pacote) exercitarem a checagem com o banco real sem reiniciar o contexto. */
    public void verificar() {
        if (chaveConfirmada) {
            // Deixada ligada, a confirmação aceitaria em silêncio a 1ª subida de uma versão futura sem âncora (uma rotação, um banco
            // restaurado sem a tabela de verificadores). Por isso o lembrete a cada subida, e não só quando ela é usada.
            log.warn("CPF_HASH_KEY_CONFIRMED=true: a recusa de subida sem prova da chave do hash do CPF está DESLIGADA. "
                    + "Retire a variável depois da primeira subida.");
        }
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
            if (!MessageDigest.isEqual(gravado.get().getVerificador().getBytes(StandardCharsets.UTF_8),
                    verificadorCalculado.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalStateException(
                        "A chave do hash do CPF da versão " + versao + " não é mais a mesma que calculou os hashes já "
                                + "gravados nessa versão — confira " + variavel + ".");
            }
            return;
        }
        // 1ª subida desta versão: antes de confiar na chave, cruza contra uma conta cujo CPF decifrado já prova o hash
        Cruzamento cruzamento = cruzar(versao);
        if (cruzamento == Cruzamento.NAO_CONFERE) {
            throw new IllegalStateException(
                    variavel + " não corresponde ao que os hashes já gravados na versão " + versao + " esperam.");
        }
        if (cruzamento == Cruzamento.SEM_ANCORA) {
            // Só prestador tem o CPF cifrado; o hash do cliente não se prova (não há CPF em claro para recalcular). Sem nenhum
            // prestador decifrável nessa versão, uma chave digitada errada seria gravada como referência sem que nada a desminta.
            long contas = userRepository.countByCpfHashIsNotNullAndCpfHashVersao(versao);
            if (contas > 0) {
                if (!chaveConfirmada) {
                    // Recusa ANTES de gravar: nada fica registrado como referência, e a próxima subida (com a chave corrigida ou
                    // confirmada) recomeça deste ponto.
                    throw new IllegalStateException(
                            contas + " conta(s) têm o hash do CPF na versão " + versao + ", mas nenhum prestador com CPF decifrável "
                                    + "permite PROVAR que " + variavel + " é a chave certa — gravá-la agora como referência fixaria um "
                                    + "valor que ninguém conferiu, e a unicidade do CPF deixaria de enxergar essas contas se estiver "
                                    + "errado. Confira o valor de " + variavel + " (tem de ser exatamente o que calculou esses hashes) "
                                    + "e, só se tiver certeza, suba UMA vez com CPF_HASH_KEY_CONFIRMED=true; depois retire a variável.");
                }
                log.warn("{} conta(s) na versão {} aceitas SEM prova de que {} é a chave certa (CPF_HASH_KEY_CONFIRMED=true). "
                        + "O verificador gravado agora vira a referência.", contas, versao, variavel);
            }
        }
        verificacaoRepository.save(new CpfHashKeyVerificacao(versao, verificadorCalculado));
    }

    private Cruzamento cruzar(int versao) {
        if (!cruzamentoAtivo) {
            // Sem como consultar a âncora é "nenhuma âncora" — NÃO "conferido". Devolver CONFERE aqui fazia a flag do backfill
            // (marketplace.cpf-backfill.enabled=false, que só deveria pular o backfill) desligar também a recusa de subida sem prova
            // da chave. Onde não há conta na versão (o H2 dos testes de contexto) nada muda: sem contas, não há o que provar.
            return Cruzamento.SEM_ANCORA;
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
            return cpfHashService.confere(cpfDecifrado, user.getCpfHash(), user.getCpfHashVersao())
                    ? Cruzamento.CONFERE : Cruzamento.NAO_CONFERE;
        }
        return Cruzamento.SEM_ANCORA;   // nenhuma âncora decifrável: confia na 1ª subida desta versão (trust-on-first-use)
    }
}
