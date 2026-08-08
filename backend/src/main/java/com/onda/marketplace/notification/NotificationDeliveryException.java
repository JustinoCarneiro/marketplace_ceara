package com.onda.marketplace.notification;

/**
 * Falha ao entregar um alerta por um canal externo (e-mail, push).
 *
 * <p>Existe para que "não consegui avisar o admin" seja um evento observável, e não
 * um {@code log.warn} enterrado: no caminho do SOS quem trata isso é o
 * {@code OutboxProcessor}, que marca o evento como FALHA — aí a pendência aparece na
 * reconciliação do painel (US27) e pode ser reprocessada.
 *
 * <p>Canal deliberadamente desligado (NoOp em dev) <b>não</b> lança: ausência de canal
 * configurado é assunto do {@link AlertChannelValidator}, na subida da aplicação.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
