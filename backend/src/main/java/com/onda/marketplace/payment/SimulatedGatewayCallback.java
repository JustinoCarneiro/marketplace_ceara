package com.onda.marketplace.payment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Emula a confirmação assíncrona que um gateway real enviaria ao nosso webhook.
 *
 * <p>Sem isso, em ambiente sem gateway de verdade ({@link GatewayServiceImpl} é stub),
 * nenhuma cobrança sai de {@code PENDENTE}: o OutboxProcessor despacha a cobrança e
 * ninguém nunca confirma. O escrow ficava travado e o prestador nunca via "pago e retido"
 * (US07) fora dos testes, que postam o webhook na mão.
 *
 * <p>Entra pelo <b>mesmo</b> caminho do webhook de produção
 * ({@link PaymentService#confirmPayment}) — o estado financeiro continua sendo dirigido
 * por evento confirmado, nunca por transação de banco (princípio Escrow do CLAUDE.md).
 * Trocar o stub por Stripe/MercadoPago não muda mais nada além de desligar esta simulação.
 *
 * <p><b>Desligado por padrão.</b> Exige opt-in explícito
 * ({@code marketplace.gateway.simulate-confirmation=true}) para que produção nunca
 * confirme pagamento sozinha por esquecimento de configuração — o default seguro é
 * "não invente confirmação de dinheiro".
 */
@Component
@ConditionalOnProperty(name = "marketplace.gateway.simulate-confirmation", havingValue = "true")
class SimulatedGatewayCallback {

    private static final Logger log = LoggerFactory.getLogger(SimulatedGatewayCallback.class);

    private final TransactionRepository transactionRepository;
    private final PaymentService        paymentService;

    SimulatedGatewayCallback(TransactionRepository transactionRepository,
                             PaymentService paymentService) {
        this.transactionRepository = transactionRepository;
        this.paymentService        = paymentService;
    }

    @PostConstruct
    void avisar() {
        log.warn("=== GATEWAY SIMULADO ATIVO: cobranças serão confirmadas automaticamente. "
                + "NUNCA use este modo em produção. ===");
    }

    /**
     * Confirma cobranças já despachadas ao gateway (têm {@code gatewayTransactionId})
     * e ainda pendentes. Varredura em vez de callback agendado no momento da cobrança:
     * sobrevive a restart do app e é naturalmente idempotente.
     */
    @Scheduled(fixedDelayString = "${marketplace.gateway.simulate-delay-ms:1000}")
    void confirmarCobrancasDespachadas() {
        List<Transaction> aguardando = transactionRepository
                .findByStatusPagamentoAndGatewayTransactionIdIsNotNull(TransactionStatus.PENDENTE);

        for (Transaction tx : aguardando) {
            log.info("Gateway simulado: confirmando pagamento da tx={} ({})",
                    tx.getId(), tx.getGatewayTransactionId());
            paymentService.confirmPayment(tx.getGatewayTransactionId(), "PAGO");
        }
    }
}
