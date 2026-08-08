package com.onda.marketplace.notification;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * US30: o alerta de SOS "nunca depende só do painel". Sem esta validação, faltar a
 * credencial de SMTP deixava a plataforma rodando sem nenhum canal de emergência, em
 * silêncio — o único vestígio era uma linha de log.
 */
class AlertChannelValidatorTest {

    /** Canal configurado e funcionando. */
    private static final EmailSender EMAIL_ATIVO = new EmailSender() {
        @Override public void enviar(String tipo, UUID refId) { }
    };
    private static final PushSender PUSH_ATIVO = new PushSender() {
        @Override public void enviar(String tipo, UUID refId) { }
    };

    private final EmailSender emailDesligado = new NoOpEmailSender();
    private final PushSender  pushDesligado  = new NoOpPushSender();

    @Test
    void semCanalNenhum_recusaSubir() {
        var validator = new AlertChannelValidator(emailDesligado, pushDesligado, false);

        assertThatThrownBy(validator::validar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhum canal de alerta externo configurado")
                .hasMessageContaining("allow-missing-alert-channel");
    }

    @Test
    void semCanal_masComOptOutExplicito_sobe() {
        // dev/demo/CI: a ausência é intencional e declarada
        var validator = new AlertChannelValidator(emailDesligado, pushDesligado, true);

        assertThatCode(validator::validar).doesNotThrowAnyException();
    }

    @Test
    void comEmail_sobe() {
        var validator = new AlertChannelValidator(EMAIL_ATIVO, pushDesligado, false);

        assertThatCode(validator::validar).doesNotThrowAnyException();
    }

    @Test
    void comPush_sobe() {
        // um canal basta — push sozinho já tira o SOS da dependência do painel
        var validator = new AlertChannelValidator(emailDesligado, PUSH_ATIVO, false);

        assertThatCode(validator::validar).doesNotThrowAnyException();
    }
}
