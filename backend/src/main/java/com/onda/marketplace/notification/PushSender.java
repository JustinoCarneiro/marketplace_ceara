package com.onda.marketplace.notification;

import java.util.UUID;

/**
 * Contrato para envio de push notification ao admin (M12).
 * Implementação de produção: Firebase FCM (v2+) — adicionada quando o app
 * mobile for publicado.
 * Implementação de MVP: {@link NoOpPushSender} (stub sem efeito externo).
 */
public interface PushSender {

    /**
     * Envia push notification ao dispositivo do admin.
     *
     * @param tipo  tipo do alerta (SOS | DISPUTA | VERIFICACAO)
     * @param refId UUID do registro de origem
     */
    void enviar(String tipo, UUID refId);
}
