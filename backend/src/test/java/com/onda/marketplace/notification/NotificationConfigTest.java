package com.onda.marketplace.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationConfigTest {
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfig.class)
            .withBean(JavaMailSender.class, () -> mail);

    @Test
    void smtpLoginIsIndependentOfVerifiedSender() {
        context.withPropertyValues("spring.mail.username=resend", "spring.mail.password=test-key",
                "notification.from-email=contato@example.org").run(ctx -> {
            assertThat(ctx.getBean(EmailSender.class).ativo()).isTrue();
            ctx.getBean(UserMailSender.class).enviar("destino@example.org", "Assunto", "Mensagem");
            verify(mail).send(argThat((SimpleMailMessage message) ->
                    "contato@example.org".equals(message.getFrom())));
        });
    }

    @Test
    void blankSenderFallsBackToEmailLoginForExistingProviders() {
        context.withPropertyValues("spring.mail.username=contato@example.org", "spring.mail.password=test-key",
                "notification.from-email=").run(ctx -> {
            assertThat(ctx.getBean(EmailSender.class).ativo()).isTrue();
            ctx.getBean(UserMailSender.class).enviar("destino@example.org", "Assunto", "Mensagem");
            verify(mail).send(argThat((SimpleMailMessage message) -> "contato@example.org".equals(message.getFrom())));
        });
    }

    @Test
    void incompleteCredentialsDoNotEnableAlertChannel() {
        context.withPropertyValues("spring.mail.username=resend",
                "notification.from-email=contato@example.org").run(ctx -> {
            assertThat(ctx.getBean(EmailSender.class).ativo()).isFalse();
            assertThat(ctx.getBean(UserMailSender.class)).isInstanceOf(NoOpUserMailSender.class);
            verify(mail, never()).send(any(SimpleMailMessage.class));
        });
    }

    @Test
    void nonEmailLoginRequiresExplicitSender() {
        context.withPropertyValues("spring.mail.username=resend", "spring.mail.password=test-key")
                .run(ctx -> assertThat(ctx.getBean(EmailSender.class).ativo()).isFalse());
    }
}
