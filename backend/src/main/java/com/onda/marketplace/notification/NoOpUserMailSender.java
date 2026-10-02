package com.onda.marketplace.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sem canal de e-mail ao usuário (SMTP não configurado e nenhum destino de arquivo declarado).
 * Não grava nada do conteúdo: o texto pode ser um código de uso único.
 */
public class NoOpUserMailSender implements UserMailSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpUserMailSender.class);

    @Override
    public void enviar(String para, String assunto, String texto) {
        log.warn("[NoOpUserMailSender] E-mail \"{}\" não enviado: nenhum canal de e-mail ao usuário configurado.", assunto);
    }

    @Override
    public boolean ativo() { return false; }
}
