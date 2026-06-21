package com.onda.marketplace.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Registra a implementação correta de {@link EmailSender} dependendo
 * da presença de {@link JavaMailSender} no contexto.
 *
 * @ConditionalOnBean / @ConditionalOnMissingBean são confiáveis apenas
 * em classes @Configuration (não em @Component de scan de componentes),
 * pois a ordem de avaliação dos beans é determinística aqui.
 */
@Configuration
class NotificationConfig {

    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    EmailSender javaMailEmailSender(
            JavaMailSender javaMailSender,
            @Value("${notification.admin-email:admin@marketplace-ceara.com.br}") String adminEmail,
            @Value("${spring.mail.username:}") String fromEmail) {
        return new JavaMailEmailSender(javaMailSender, adminEmail, fromEmail);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender noOpEmailSender() {
        return new NoOpEmailSender();
    }
}
