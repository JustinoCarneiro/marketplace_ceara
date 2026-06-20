package com.onda.marketplace.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub de {@link PushSender} para o MVP — apenas loga a intenção de envio.
 * Substitua por {@code FcmPushSender} (Firebase Cloud Messaging) quando o
 * app mobile for publicado na loja.
 */
@Component
public class NoOpPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushSender.class);

    @Override
    public void enviar(String tipo, UUID refId) {
        log.info("[PUSH-NOOP] Alerta {} seria enviado via push (ref={}). " +
                 "Implemente FcmPushSender para habilitar push real.", tipo, refId);
    }
}
