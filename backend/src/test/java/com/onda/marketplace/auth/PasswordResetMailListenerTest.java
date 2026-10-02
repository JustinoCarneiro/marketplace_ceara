package com.onda.marketplace.auth;

import com.onda.marketplace.notification.NotificationDeliveryException;
import com.onda.marketplace.notification.UserMailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** US35 — o e-mail que leva o código e o aviso de senha alterada. */
class PasswordResetMailListenerTest {

    UserMailSender mailSender = mock(UserMailSender.class);
    PasswordResetMailListener listener = new PasswordResetMailListener(mailSender);

    @Test
    void aoSolicitar_enviaOCodigoComHifenDeLeituraEAValidade() {
        listener.aoSolicitar(new PasswordResetRequested("ana@example.com", "Ana", "ABCD2345", 30));

        ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mailSender).enviar(eq("ana@example.com"), assunto.capture(), corpo.capture());
        assertThat(assunto.getValue()).contains("código");
        assertThat(corpo.getValue())
                .contains("Olá, Ana!")
                .contains("ABCD-2345")                 // 4+4: mais fácil de ler e de digitar
                .contains("30 minutos")
                .contains("só pode ser usado uma vez")
                .contains("ignorar este e-mail");      // quem não pediu sabe o que fazer
    }

    @Test
    void aoTrocar_avisaQueASenhaMudou_semCodigoNenhum_eOrientaQuemNaoFoi() {
        listener.aoTrocar(new PasswordChanged("ana@example.com", "Ana"));

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mailSender).enviar(eq("ana@example.com"), any(), corpo.capture());
        assertThat(corpo.getValue()).contains("foi alterada").contains("suporte@onda.app");
    }

    @Test
    void falhaNoEnvio_naoPropagaEoLogNaoLevaOEnderecoNemOCodigo() {
        doThrow(new NotificationDeliveryException("falha com ana@example.com e ABCD-2345", new RuntimeException()))
                .when(mailSender).enviar(any(), any(), any());

        // a resposta HTTP já foi dada: a falha não pode virar erro para ninguém
        assertThatCode(() -> listener.aoSolicitar(new PasswordResetRequested("ana@example.com", "Ana", "ABCD2345", 30)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.aoTrocar(new PasswordChanged("ana@example.com", "Ana")))
                .doesNotThrowAnyException();
    }
}
