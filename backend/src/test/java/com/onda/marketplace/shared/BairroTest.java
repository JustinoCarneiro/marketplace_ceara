package com.onda.marketplace.shared;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BairroTest {

    @Test
    void null_ouVazio_passam_campoOpcional() {
        assertThat(Bairro.valido(null)).isTrue();
        assertThat(Bairro.valido("")).isTrue();
        assertThat(Bairro.valido("   ")).isTrue();
    }

    @Test
    void nomeDaLista_passa() {
        assertThat(Bairro.valido("Meireles")).isTrue();
        assertThat(Bairro.valido("Barra do Ceará")).isTrue();
    }

    @Test
    void foraDaLista_naoPassa() {
        // o caso concreto que a correção fecha: endereço, nome ou telefone no lugar do bairro
        assertThat(Bairro.valido("Rua das Flores 123")).isFalse();
        assertThat(Bairro.valido("85999990000")).isFalse();
        assertThat(Bairro.valido("José da Silva")).isFalse();
    }

    @Test
    void sensivelAMaiusculasEMinusculas() {
        assertThat(Bairro.valido("meireles")).isFalse();
        assertThat(Bairro.valido("MEIRELES")).isFalse();
    }
}
