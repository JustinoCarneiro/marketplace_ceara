package com.onda.marketplace.servicerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Roda a expiração de pedidos sem andamento (ver {@link ServiceRequestExpirationService}); de hora em hora. */
@Component
public class PedidoExpiracaoJob {

    private static final Logger log = LoggerFactory.getLogger(PedidoExpiracaoJob.class);

    private final ServiceRequestExpirationService expiracao;

    public PedidoExpiracaoJob(ServiceRequestExpirationService expiracao) {
        this.expiracao = expiracao;
    }

    @Scheduled(fixedDelayString = "${marketplace.request.expiration-job-delay-ms:3600000}")
    public void expirarParados() {
        int cancelados = expiracao.expirar(Instant.now());
        if (cancelados > 0) {
            log.info("Pedidos sem andamento: {} cancelado(s) por expiração.", cancelados);
        }
    }
}
