package com.onda.marketplace.notification;

/**
 * E-mail transacional enviado a um USUÁRIO (hoje: recuperação de senha, US35).
 *
 * <p>É um contrato à parte de {@link EmailSender}, que só avisa o admin sobre alertas
 * operacionais (SOS, disputa) para um endereço fixo de configuração. Aqui o destinatário e o
 * texto vêm de quem chama.
 *
 * <p>Implementações: {@link JavaMailUserMailSender} (SMTP configurado),
 * {@link FileSinkUserMailSender} (só dev/CI: grava em arquivo) e {@link NoOpUserMailSender}
 * (sem canal).
 */
public interface UserMailSender {

    /**
     * @throws NotificationDeliveryException se o canal está ativo e o envio falhou
     */
    void enviar(String para, String assunto, String texto);

    /**
     * {@code false} quando não há entrega nenhuma acontecendo (sem SMTP). Quem oferece um fluxo
     * que depende do e-mail (recuperação de senha) usa isto para avisar que está indisponível em
     * vez de prometer uma mensagem que nunca vai sair.
     */
    default boolean ativo() { return true; }
}
