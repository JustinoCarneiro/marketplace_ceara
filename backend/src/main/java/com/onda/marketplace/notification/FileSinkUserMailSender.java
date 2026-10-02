package com.onda.marketplace.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Só para dev/CI: em vez de mandar o e-mail, grava a mensagem num arquivo ({@code <dir>/<instante>-<destino>.txt}).
 * Existe para os testes de ponta a ponta lerem o código de recuperação de senha sem um SMTP de verdade.
 *
 * <p>Não há endpoint nem log que exponha o conteúdo — só o arquivo, no diretório que quem sobe a
 * aplicação declarou em {@code notification.mail-sink-dir}. NUNCA em produção: o código de
 * recuperação ficaria legível em disco. Só vale quando não há SMTP configurado (o SMTP real tem
 * prioridade em {@link NotificationConfig}).
 */
public class FileSinkUserMailSender implements UserMailSender {

    private final Path dir;

    public FileSinkUserMailSender(Path dir) {
        this.dir = dir;
    }

    @Override
    public void enviar(String para, String assunto, String texto) {
        try {
            Files.createDirectories(dir);
            // nome seguro: o destino vem de entrada do usuário (nada de barra ou ".." no caminho)
            String seguro = para.replaceAll("[^A-Za-z0-9@._+-]", "_");
            Path arquivo = dir.resolve(System.currentTimeMillis() + "-" + System.nanoTime() % 1_000_000 + "-" + seguro + ".txt");
            Files.writeString(arquivo, "Para: " + para + "\nAssunto: " + assunto + "\nEnviado em: " + Instant.now()
                    + "\n\n" + texto, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new NotificationDeliveryException("Falha ao gravar o e-mail de desenvolvimento em " + dir, ex);
        }
    }
}
