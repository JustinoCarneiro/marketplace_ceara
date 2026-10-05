package com.onda.marketplace.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Recusa subir se houver conta com hash de CPF de uma versão de chave que a configuração não sabe mais calcular.
 *
 * <p>Sem esta conferência, esquecer {@code CPF_HASH_KEY_PREVIOUS} na primeira subida depois da separação das chaves não
 * dá erro nenhum: a unicidade deixa de enxergar essas contas, e o cliente que já confirmou o CPF fica sem conseguir
 * confirmar de novo (o hash dele não confere com nenhuma chave configurada). Subir e deixar o problema escondido é pior
 * que não subir; num deploy em rolagem a versão anterior continua no ar. Roda antes do {@code ProviderCpfBackfill}.
 */
@Component
@Order(1)
public class CpfHashKeyCheck implements ApplicationRunner {

    private final UserRepository  userRepository;
    private final CpfHashService  cpfHashService;

    public CpfHashKeyCheck(UserRepository userRepository, CpfHashService cpfHashService) {
        this.userRepository = userRepository;
        this.cpfHashService = cpfHashService;
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
    }
}
