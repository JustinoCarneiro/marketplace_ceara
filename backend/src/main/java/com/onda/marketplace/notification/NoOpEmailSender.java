package com.onda.marketplace.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback de {@link EmailSender} quando {@code spring.mail.host} não está configurado
 * (ex.: testes, ambientes sem SMTP). Loga a intenção sem enviar nada.
 */
@Component
@ConditionalOnMissingBean(JavaMailEmailSender.class)
public class NoOpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailSender.class);

    @Override
    public void enviar(String tipo, UUID refId) {
        log.info("[NoOpEmailSender] Alerta {} (ref={}) — e-mail não configurado, ignorado.", tipo, refId);
    }
}
