package com.onda.marketplace.auth;

import com.onda.marketplace.notification.UserMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa por e-mail que a conta foi excluída (US36) — DEPOIS do commit e fora da requisição.
 *
 * <p>Depois do commit: não sai aviso de uma exclusão que o rollback desfez. Fora da requisição: o
 * envio SMTP leva segundos e a exclusão não espera por ele. Falha de envio nunca desfaz nada (a conta
 * já foi anonimizada) e o log só leva a classe da falha, nunca o endereço.
 */
@Component
public class AccountDeletedMailListener {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletedMailListener.class);

    private final UserMailSender mailSender;

    AccountDeletedMailListener(UserMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoExcluir(AccountDeleted evento) {
        try {
            // Texto corrigido na revisão cruzada (2026-10-05): a versão anterior prometia "nenhuma informação
            // que identifique você" — falso, o fluxo retém IP do aceite dos termos, coordenadas de SOS e o
            // texto de denúncias (ver memoria-tecnica/decisoes/exclusao-de-conta-por-anonimizacao.md).
            mailSender.enviar(evento.email(), "Onda — sua conta foi excluída", """
                    Olá, %s!

                    Sua conta no Onda foi excluída, como você pediu. Seu nome e e-mail saíram de tudo que você \
                    usava no app. Por exigência legal, alguns registros continuam guardados — o histórico de \
                    pagamentos e avaliações, o aceite dos termos de uso (com o IP de quando você aceitou) e \
                    eventuais alertas de segurança ou denúncias que você tenha feito ou recebido —, mas sem o \
                    seu nome.

                    Se NÃO foi você, fale com o suporte em suporte@onda.app.

                    Marketplace Ceará
                    """.formatted(evento.nome()));
        } catch (RuntimeException ex) {
            // só a classe da falha: a mensagem poderia trazer o endereço
            log.warn("Falha ao enviar o aviso de exclusão de conta ({})", ex.getClass().getSimpleName());
        }
    }
}
