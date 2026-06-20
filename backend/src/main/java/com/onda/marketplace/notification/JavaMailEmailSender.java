package com.onda.marketplace.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementação real de {@link EmailSender} via {@link JavaMailSender}.
 * Ativada somente quando {@link JavaMailSender} está disponível no contexto
 * (i.e., {@code spring.mail.host} configurado). Sem configuração de e-mail,
 * o {@link NoOpEmailSender} assume automaticamente.
 */
@Component
@ConditionalOnBean(JavaMailSender.class)
public class JavaMailEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(JavaMailEmailSender.class);

    private final JavaMailSender javaMailSender;

    @Value("${notification.admin-email:admin@marketplace-ceara.com.br}")
    private String adminEmail;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public JavaMailEmailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void enviar(String tipo, UUID refId) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail.isBlank() ? adminEmail : fromEmail);
            msg.setTo(adminEmail);
            msg.setSubject("[Marketplace Ceará] ALERTA: " + label(tipo));
            msg.setText(corpo(tipo, refId));
            javaMailSender.send(msg);
            log.info("E-mail de alerta {} enviado para {} (ref={})", tipo, adminEmail, refId);
        } catch (MailException ex) {
            // Falha de envio NÃO é fatal — alerta já persistido no banco
            log.warn("Falha ao enviar e-mail de alerta {} (ref={}): {}", tipo, refId, ex.getMessage());
        }
    }

    private String label(String tipo) {
        return switch (tipo) {
            case "SOS"          -> "SOS acionado durante atendimento";
            case "DISPUTA"      -> "Disputa aberta — requer mediação";
            case "VERIFICACAO"  -> "Verificação de prestador inconclusiva";
            default             -> tipo;
        };
    }

    private String corpo(String tipo, UUID refId) {
        return """
                Marketplace Ceará — Alerta Operacional
                ────────────────────────────────────────
                Tipo   : %s
                Ref ID : %s

                Acesse o painel admin para visualizar o detalhe.
                """.formatted(label(tipo), refId);
    }
}
