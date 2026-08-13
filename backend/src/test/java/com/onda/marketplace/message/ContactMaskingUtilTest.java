package com.onda.marketplace.message;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactMaskingUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "me chama no (85) 99999-9999 que é mais rápido",
            "85999999999 esse é meu zap",
            "liga pra +55 85 99999-9999",
            "meu numero eh 99999-9999",
            "manda no whats 85 99999 9999",
    })
    void mascarar_telefoneEmVariosFormatos_remove(String texto) {
        var r = ContactMaskingUtil.mascarar(texto);
        assertThat(r.mascarado()).as("deveria mascarar: " + texto).isTrue();
        assertThat(r.texto()).contains("[contato removido]");
        assertThat(r.texto()).doesNotContain("99999");
    }

    @Test
    void mascarar_email_remove() {
        var r = ContactMaskingUtil.mascarar("me manda um email pra joao.silva@gmail.com por favor");
        assertThat(r.mascarado()).isTrue();
        assertThat(r.texto()).isEqualTo("me manda um email pra [contato removido] por favor");
    }

    @Test
    void mascarar_telefoneEEmailJuntos_removeOsDois() {
        var r = ContactMaskingUtil.mascarar("fala comigo: joao@teste.com ou (85) 98888-7777");
        assertThat(r.mascarado()).isTrue();
        assertThat(r.texto()).doesNotContain("@teste.com");
        assertThat(r.texto()).doesNotContain("98888");
    }

    @Test
    void mascarar_textoSemContato_naoAlteraNada() {
        var r = ContactMaskingUtil.mascarar("o vazamento é embaixo da pia, dá pra vir amanhã de manhã?");
        assertThat(r.mascarado()).isFalse();
        assertThat(r.texto()).isEqualTo("o vazamento é embaixo da pia, dá pra vir amanhã de manhã?");
    }

    @Test
    void mascarar_data_naoConfundeComTelefone() {
        // "/" não é separador válido no regex de telefone — evita falso positivo em data.
        var r = ContactMaskingUtil.mascarar("posso ir dia 13/08/2026");
        assertThat(r.mascarado()).isFalse();
    }

    @Test
    void mascarar_valorMonetario_naoConfundeComTelefone() {
        var r = ContactMaskingUtil.mascarar("o serviço fica R$ 150,00");
        assertThat(r.mascarado()).isFalse();
    }

    @Test
    void mascarar_nulo_naoQuebra() {
        var r = ContactMaskingUtil.mascarar(null);
        assertThat(r.mascarado()).isFalse();
        assertThat(r.texto()).isNull();
    }
}
