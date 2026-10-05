package com.onda.marketplace.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.onda.marketplace.notification.NotificationDeliveryException;
import com.onda.marketplace.notification.UserMailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** US36 — o aviso por e-mail de que a conta foi excluída. */
class AccountDeletedMailListenerTest {

    UserMailSender mailSender = mock(UserMailSender.class);
    AccountDeletedMailListener listener = new AccountDeletedMailListener(mailSender);

    final Logger logDoListener = (Logger) LoggerFactory.getLogger(AccountDeletedMailListener.class);
    final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capturaLogs() {
        logs.start();
        logDoListener.addAppender(logs);
    }

    @AfterEach
    void soltaLogs() {
        logDoListener.detachAppender(logs);
    }

    @Test
    void aoExcluir_avisaQueAContaFoiExcluida_eOQueFicaGuardado() {
        listener.aoExcluir(new AccountDeleted("ana@example.com", "Ana"));

        ArgumentCaptor<String> assunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(mailSender).enviar(eq("ana@example.com"), assunto.capture(), corpo.capture());
        assertThat(assunto.getValue()).contains("excluída");
        assertThat(corpo.getValue())
                .contains("Olá, Ana!")
                .contains("foi excluída")
                .contains("sem nenhuma informação que identifique você")   // o que a pessoa precisa saber que fica
                .contains("suporte@onda.app");                              // quem não foi sabe a quem recorrer
    }

    @Test
    void configuracao_soDepoisDoCommit_eForaDaRequisicao() throws Exception {
        // Mock não executa a anotação, então só dá para conferi-la aqui: sem AFTER_COMMIT sairia aviso de uma
        // exclusão que o rollback desfez; sem @Async o SMTP seguraria a resposta da exclusão.
        var metodo = AccountDeletedMailListener.class.getMethod("aoExcluir", AccountDeleted.class);

        assertThat(metodo.getAnnotation(TransactionalEventListener.class).phase())
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(metodo.isAnnotationPresent(Async.class)).isTrue();
    }

    @Test
    void falhaNoEnvio_naoPropaga_eOLogNaoLevaOEnderecoNemOTexto() {
        doThrow(new NotificationDeliveryException("falha com ana@example.com", new RuntimeException()))
                .when(mailSender).enviar(any(), any(), any());

        // a exclusão já foi confirmada: a falha do e-mail não pode virar erro nem desfazê-la
        assertThatCode(() -> listener.aoExcluir(new AccountDeleted("ana@example.com", "Ana")))
                .doesNotThrowAnyException();

        assertThat(logs.list).hasSize(1);
        String log = logs.list.get(0).getFormattedMessage();
        assertThat(log).contains("NotificationDeliveryException")
                .doesNotContain("ana@example.com")
                .doesNotContain("Ana");
    }
}
