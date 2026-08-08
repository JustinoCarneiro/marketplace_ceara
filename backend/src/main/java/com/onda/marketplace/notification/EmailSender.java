package com.onda.marketplace.notification;

import java.util.UUID;

/**
 * Contrato para envio de e-mail de alerta ao admin (M12).
 * Implementação real: {@link JavaMailEmailSender}.
 * Em testes: mock via Mockito.
 */
public interface EmailSender {

    /**
     * Envia e-mail de alerta para o endereço configurado em
     * {@code notification.admin-email}.
     *
     * @param tipo  tipo do alerta (SOS | DISPUTA | VERIFICACAO)
     * @param refId UUID do registro de origem
     * @throws NotificationDeliveryException se o canal está ativo e o envio falhou
     */
    void enviar(String tipo, UUID refId);

    /**
     * {@code false} quando este é um canal desligado (NoOp), ou seja, quando não há
     * entrega nenhuma acontecendo. Usado pelo {@link AlertChannelValidator} para não
     * deixar produção subir sem canal de emergência.
     */
    default boolean ativo() { return true; }
}
