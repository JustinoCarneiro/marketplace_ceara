package com.onda.marketplace.notification;

import java.util.UUID;

/**
 * Contrato para envio de push notification ao admin (M12).
 * Implementação de produção: FCM HTTP v1, ativada por configuração e cadastro
 * de dispositivo administrativo.
 * Implementação de MVP: {@link NoOpPushSender} (stub sem efeito externo).
 */
public interface PushSender {

    /**
     * Envia push notification ao dispositivo do admin.
     *
     * @param tipo  tipo do alerta (SOS | DISPUTA | VERIFICACAO)
     * @param refId UUID do registro de origem
     * @throws NotificationDeliveryException se o canal está ativo e o envio falhou
     */
    void enviar(String tipo, UUID refId);

    /**
     * {@code false} quando este é um canal desligado (NoOp) — ver
     * {@link EmailSender#ativo()}.
     */
    default boolean ativo() { return true; }
}
