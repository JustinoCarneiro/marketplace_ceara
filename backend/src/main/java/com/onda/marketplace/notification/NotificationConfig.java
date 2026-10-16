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
 * Usuário e senha precisam estar preenchidos; o remetente é configurado separadamente em
 * {@code notification.from-email} (login SMTP pode ser apenas "resend").
 * Sem configuração completa é NoOp declarado — e o {@link AlertChannelValidator}
 * impede que isso passe despercebido em produção.
 */
@Configuration
class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    EmailSender emailSender(
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            @Value("${notification.admin-email:admin@marketplace-ceara.com.br}") String adminEmail,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${notification.from-email:${spring.mail.username:}}") String fromEmail) {

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        String sender = fromEmail.isBlank() ? username : fromEmail;

        if (javaMailSender == null || !configured(username, password, sender)) {
            log.warn("E-mail de alerta DESLIGADO: credenciais SMTP ou remetente incompletos. "
                    + "Alertas operacionais (inclusive SOS) só existirão no painel.");
            return new NoOpEmailSender();
        }

        log.info("E-mail de alerta ativo — destinatário: {}", adminEmail);
        return new JavaMailEmailSender(javaMailSender, adminEmail, sender);
    }

    /**
     * E-mail ao USUÁRIO (recuperação de senha, US35). Mesma regra de credencial do alerta: sem
     * usuário, senha e remetente válido não há SMTP de verdade. Sem ele, só vira "gravar em arquivo" se
     * alguém declarar {@code notification.mail-sink-dir} de propósito (dev/CI); senão fica
     * desligado e o fluxo avisa que está indisponível.
     */
    @Bean
    UserMailSender userMailSender(
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${notification.from-email:${spring.mail.username:}}") String fromEmail,
            @Value("${notification.mail-sink-dir:}") String sinkDir) {

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        String sender = fromEmail.isBlank() ? username : fromEmail;

        if (javaMailSender != null && configured(username, password, sender)) {
            return new JavaMailUserMailSender(javaMailSender, sender);
        }
        if (!sinkDir.isBlank()) {
            log.warn("=== E-MAIL AO USUÁRIO GRAVADO EM ARQUIVO ({}): só dev/CI. NUNCA em produção — "
                    + "o código de recuperação de senha fica legível em disco. ===", sinkDir);
            return new FileSinkUserMailSender(java.nio.file.Path.of(sinkDir));
        }
        log.warn("E-mail ao usuário DESLIGADO: sem SMTP completo e remetente válido e sem notification.mail-sink-dir. "
                + "A recuperação de senha responderá que está indisponível.");
        return new NoOpUserMailSender();
    }

    private static boolean configured(String username, String password, String fromEmail) {
        if (username.isBlank() || password.isBlank()) return false;
        try {
            var address = new jakarta.mail.internet.InternetAddress(fromEmail, true);
            address.validate();
            return address.getAddress().contains("@");
        } catch (jakarta.mail.internet.AddressException ex) {
            return false;
        }
    }
}
