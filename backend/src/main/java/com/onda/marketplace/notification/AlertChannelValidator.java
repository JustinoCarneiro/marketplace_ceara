package com.onda.marketplace.notification;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Impede que a aplicação suba sem nenhum canal de alerta externo configurado.
 *
 * <p>A US30 promete que o alerta de SOS "nunca depende só do painel". Sem esta checagem
 * a promessa era silenciosa: bastava faltar credencial de SMTP para nenhum aviso sair, e
 * ninguém descobria — o único vestígio era uma linha de log entre milhares, com o health
 * check de e-mail desligado por cima.
 *
 * <p>SOS é caminho de segurança de pessoas: o default é <b>recusar a subida</b>. Quem
 * roda sem canal (dev, demo, CI) declara isso explicitamente com
 * {@code notification.allow-missing-alert-channel=true} — some o risco de acontecer por
 * esquecimento, mas continua sendo possível de propósito.
 */
@Component
class AlertChannelValidator {

    private static final Logger log = LoggerFactory.getLogger(AlertChannelValidator.class);

    private final EmailSender emailSender;
    private final PushSender  pushSender;
    private final boolean     permitirSemCanal;

    AlertChannelValidator(EmailSender emailSender,
                          PushSender pushSender,
                          @Value("${notification.allow-missing-alert-channel:false}") boolean permitirSemCanal) {
        this.emailSender      = emailSender;
        this.pushSender       = pushSender;
        this.permitirSemCanal = permitirSemCanal;
    }

    @PostConstruct
    void validar() {
        if (emailSender.ativo() || pushSender.ativo()) {
            return;
        }

        if (permitirSemCanal) {
            log.warn("=== SEM CANAL DE ALERTA EXTERNO: e-mail e push desligados. "
                    + "Alertas de SOS só aparecerão no painel admin. "
                    + "Aceitável em dev/CI, NUNCA em produção. ===");
            return;
        }

        throw new IllegalStateException("""
                Nenhum canal de alerta externo configurado (e-mail e push desligados).

                O SOS (US21/US30) exige aviso fora do painel — sem isso, um acionamento de \
                emergência pode ficar horas sem ninguém ver.

                Configure MAIL_USERNAME/MAIL_PASSWORD (e ADMIN_ALERT_EMAIL) ou, se a \
                ausência for intencional (dev/demo/CI), declare:
                    notification.allow-missing-alert-channel=true
                """);
    }
}
