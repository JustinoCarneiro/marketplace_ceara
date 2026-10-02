package com.onda.marketplace.auth;

import com.onda.marketplace.notification.UserMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia os e-mails da recuperação de senha (US35) DEPOIS do commit e fora da requisição.
 *
 * <p>Depois do commit: não sai e-mail de um código que o rollback desfez. Fora da requisição
 * (assíncrono): o envio SMTP leva segundos e só aconteceria para e-mails cadastrados — se
 * rodasse na requisição, o tempo de resposta denunciaria quais e-mails existem.
 *
 * <p>Falha de envio nunca vira erro para quem pediu (a resposta já foi dada) e o log não leva o
 * endereço nem o texto: o corpo carrega um código de uso único.
 */
@Component
public class PasswordResetMailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailListener.class);

    private final UserMailSender mailSender;

    PasswordResetMailListener(UserMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSolicitar(PasswordResetRequested evento) {
        String codigo = evento.codigo().substring(0, 4) + "-" + evento.codigo().substring(4);
        enviar(evento.email(), "Onda — seu código para redefinir a senha", """
                Olá, %s!

                Seu código para redefinir a senha é:

                    %s

                Ele vale por %d minutos e só pode ser usado uma vez. Digite-o no app, em "Esqueci a senha".

                Se você não pediu isso, pode ignorar este e-mail: sua senha continua a mesma.
                Nunca compartilhe este código — nossa equipe jamais pede esse código.

                Marketplace Ceará
                """.formatted(evento.nome(), codigo, evento.validadeMinutos()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoTrocar(PasswordChanged evento) {
        enviar(evento.email(), "Onda — sua senha foi alterada", """
                Olá, %s!

                A senha da sua conta foi alterada agora há pouco e todas as sessões abertas foram encerradas.

                Se foi você, nada a fazer. Se NÃO foi você, fale com o suporte em suporte@onda.app agora mesmo.

                Marketplace Ceará
                """.formatted(evento.nome()));
    }

    private void enviar(String para, String assunto, String texto) {
        try {
            mailSender.enviar(para, assunto, texto);
        } catch (RuntimeException ex) {
            // só a classe da falha: a mensagem poderia trazer o endereço ou parte do corpo
            log.warn("Falha ao enviar e-mail da recuperação de senha ({})", ex.getClass().getSimpleName());
        }
    }
}
