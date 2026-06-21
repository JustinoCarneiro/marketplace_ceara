package com.onda.marketplace.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Fallback de {@link EmailSender} quando spring.mail.host não está configurado.
 * Instanciada por {@link NotificationConfig} quando JavaMailSender está ausente.
 */
public class NoOpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailSender.class);

    @Override
    public void enviar(String tipo, UUID refId) {
        log.info("[NoOpEmailSender] Alerta {} (ref={}) — e-mail não configurado, ignorado.", tipo, refId);
    }
}
