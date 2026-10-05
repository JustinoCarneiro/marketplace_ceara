package com.onda.marketplace.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Esquecer a chave anterior na primeira subida depois da separação das chaves não dá erro nenhum: a unicidade deixa de
 * enxergar as contas antigas e quem já confirmou o CPF fica sem conseguir confirmar de novo. A aplicação recusa subir.
 */
@ExtendWith(MockitoExtension.class)
class CpfHashKeyCheckTest {

    static final String ATUAL    = "test-cpf-hmac-key-0123456789-0123456789";
    static final String ANTERIOR = "test-cpf-aes256-key-32-chars-here!";

    @Mock UserRepository userRepository;

    @Test
    void contaNaVersaoAnterior_semAChaveAnteriorConfigurada_recusaSubir_eNaoDizONenhumCpf() {
        var check = new CpfHashKeyCheck(userRepository, new CpfHashService(ATUAL, 2));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(3L);

        assertThatThrownBy(check::verificar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 conta(s)")
                .hasMessageContaining("CPF_HASH_KEY_PREVIOUS");
    }

    @Test
    void comAChaveAnteriorConfigurada_aVersaoLogoAbaixoSeReconhece_enaoRecusa() {
        var check = new CpfHashKeyCheck(userRepository, new CpfHashService(ATUAL, 2, ANTERIOR));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(1)).thenReturn(0L);   // nada abaixo da 1

        assertThatCode(check::verificar).doesNotThrowAnyException();
    }

    @Test
    void versaoMaisAntigaQueAAnterior_naoSeReconhece_aindaQueHajaChaveAnterior() {
        // rotação 1→2→3 sem regravar quem ficou na 1: a chave da versão 1 já não está configurada
        var check = new CpfHashKeyCheck(userRepository, new CpfHashService(ATUAL, 3, ANTERIOR));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(1L);

        assertThatThrownBy(check::verificar).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semContaAntiga_sobe() {
        var check = new CpfHashKeyCheck(userRepository, new CpfHashService(ATUAL, 2));
        when(userRepository.countByCpfHashIsNotNullAndCpfHashVersaoLessThan(2)).thenReturn(0L);

        assertThatCode(check::verificar).doesNotThrowAnyException();
    }
}
