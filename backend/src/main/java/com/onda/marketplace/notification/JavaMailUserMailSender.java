package com.onda.marketplace.notification;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * {@link UserMailSender} real, via {@link JavaMailSender}. Instanciado por {@link NotificationConfig}
 * só quando há credencial de SMTP ({@code spring.mail.username}).
 */
public class JavaMailUserMailSender implements UserMailSender {

    private final JavaMailSender javaMailSender;
    private final String         fromEmail;

    JavaMailUserMailSender(JavaMailSender javaMailSender, String fromEmail) {
        this.javaMailSender = javaMailSender;
        this.fromEmail      = fromEmail;
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(para);
            msg.setSubject(assunto);
            msg.setText(texto);
            javaMailSender.send(msg);
        } catch (MailException ex) {
            // Sem o endereço nem o texto na mensagem: o corpo pode carregar um código de uso único.
            throw new NotificationDeliveryException("Falha ao enviar e-mail transacional ao usuário", ex);
        }
    }
}
