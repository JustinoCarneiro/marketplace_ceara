package com.onda.marketplace.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** CPF: formato, dígitos verificadores e a forma só de dígitos (a base da unicidade por hash). */
class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"111.444.777-35", "11144477735", " 111 444 777 35 ", "123.456.789-09", "529.982.247-25"})
    void cpfValido_comOuSemMascara(String cpf) {
        assertThat(Cpf.valido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"111.444.777-36", "123.456.789-00", "111.444.777-43", "999.999.999-99", "111.111.111-11", "00000000000",
            "1114447773", "111444777350", "abc.def.ghi-jk", "111.444.777-3a"})
    void cpfInvalido_digitoErrado_repetido_tamanhoOuLetras(String cpf) {
        // "111.444.777-43": só o PRIMEIRO dígito está errado (o certo é 3) e o segundo foi calculado a partir do errado —
        // sem este caso, tirar a checagem do primeiro dígito passaria despercebido (a mutação mostrou)
        assertThat(Cpf.valido(cpf)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cpfAusente_ehInvalido(String cpf) {
        assertThat(Cpf.valido(cpf)).isFalse();
    }

    @Test
    void soDigitos_tiraMascaraEEspacos_paraQueAMesmaPessoaTenhaUmaSoForma() {
        assertThat(Cpf.soDigitos("111.444.777-35")).isEqualTo("11144477735");
        assertThat(Cpf.soDigitos(" 111 444 777 35 ")).isEqualTo("11144477735");
    }
}
