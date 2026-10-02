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

    /**
     * E-mail ao USUÁRIO (recuperação de senha, US35). Mesma regra de credencial do alerta: sem
     * {@code spring.mail.username} não há SMTP de verdade. Sem ele, só vira "gravar em arquivo" se
     * alguém declarar {@code notification.mail-sink-dir} de propósito (dev/CI); senão fica
     * desligado e o fluxo avisa que está indisponível.
     */
    @Bean
    UserMailSender userMailSender(
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${notification.mail-sink-dir:}") String sinkDir) {

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();

        if (javaMailSender != null && !fromEmail.isBlank()) {
            return new JavaMailUserMailSender(javaMailSender, fromEmail);
        }
        if (!sinkDir.isBlank()) {
            log.warn("=== E-MAIL AO USUÁRIO GRAVADO EM ARQUIVO ({}): só dev/CI. NUNCA em produção — "
                    + "o código de recuperação de senha fica legível em disco. ===", sinkDir);
            return new FileSinkUserMailSender(java.nio.file.Path.of(sinkDir));
        }
        log.warn("E-mail ao usuário DESLIGADO: sem SMTP (spring.mail.username) e sem notification.mail-sink-dir. "
                + "A recuperação de senha responderá que está indisponível.");
        return new NoOpUserMailSender();
    }
}
