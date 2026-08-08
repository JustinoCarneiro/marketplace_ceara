package com.onda.marketplace.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Escolhe a implementação de {@link EmailSender}.
 *
 * <p>A decisão é por <b>credencial</b>, não por presença do bean {@link JavaMailSender}:
 * {@code spring.mail.host} tem default {@code smtp.gmail.com}, então o bean sempre existe
 * e o antigo {@code @ConditionalOnBean} elegia o remetente real mesmo sem usuário/senha.
 * Resultado: a aplicação parecia configurada e todo alerta morria num erro de autenticação
 * do SMTP. Sem credencial agora é NoOp declarado — e o {@link AlertChannelValidator}
 * impede que isso passe despercebido em produção.
 */
@Configuration
class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    EmailSender emailSender(
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            @Value("${notification.admin-email:admin@marketplace-ceara.com.br}") String adminEmail,
            @Value("${spring.mail.username:}") String fromEmail) {

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();

        if (javaMailSender == null || fromEmail.isBlank()) {
            log.warn("E-mail de alerta DESLIGADO: spring.mail.username não configurado. "
                    + "Alertas operacionais (inclusive SOS) só existirão no painel.");
            return new NoOpEmailSender();
        }

        log.info("E-mail de alerta ativo — destinatário: {}", adminEmail);
        return new JavaMailEmailSender(javaMailSender, adminEmail, fromEmail);
    }
}
