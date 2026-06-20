package com.onda.marketplace.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class GatewayServiceImpl implements GatewayService {

    private static final Logger log = LoggerFactory.getLogger(GatewayServiceImpl.class);

    // Stub: substituir por integração real (Stripe, Juno, MercadoPago) em produção.
    // Este método é chamado FORA de @Transactional (pelo OutboxProcessor) — princípio Escrow.
    @Override
    public String cobrar(Transaction transaction) {
        String gwId = "gw-stub-" + UUID.randomUUID();
        log.info("Gateway stub: cobrando R${} via {} → id={}", transaction.getValorTotal(),
                transaction.getMetodo(), gwId);
        return gwId;
    }
}
