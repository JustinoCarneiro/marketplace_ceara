package com.onda.marketplace.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private static final Set<String> PAYMENT_EVENTS =
            Set.of("PAYMENT_INITIATED", "PAYMENT_RELEASED", "PAYMENT_REFUNDED");

    private final OutboxEventRepository outboxRepository;
    private final TransactionRepository transactionRepository;
    private final GatewayService        gatewayService;

    public OutboxProcessor(OutboxEventRepository outboxRepository,
                           TransactionRepository transactionRepository,
                           GatewayService gatewayService) {
        this.outboxRepository      = outboxRepository;
        this.transactionRepository = transactionRepository;
        this.gatewayService        = gatewayService;
    }

    /**
     * Processa eventos pendentes. Sem @Transactional — gateway NUNCA dentro de transação
     * de banco (princípio Escrow/Saga do CLAUDE.md). Cada evento é persistido em transação
     * própria, separada da chamada ao gateway.
     */
    @Scheduled(fixedDelayString = "${marketplace.outbox.delay-ms:5000}")
    public void processOutbox() {
        List<OutboxEvent> pendentes = outboxRepository
                .findTop20ByStatusOrderByCriadoEmAsc(OutboxStatus.PENDENTE);

        for (OutboxEvent event : pendentes) {
            if (PAYMENT_EVENTS.contains(event.getTipoEvento())) {
                processarPagamento(event);
            } else {
                processarOutro(event);
            }
        }
    }

    private void processarPagamento(OutboxEvent event) {
        transactionRepository.findById(event.getAgregadoId()).ifPresent(tx -> {
            try {
                despacharParaGateway(event.getTipoEvento(), tx);
                salvarResultadoPagamento(tx, event, true);
            } catch (Exception ex) {
                log.warn("Falha ao processar {} para tx={}: {}", event.getTipoEvento(),
                        tx.getId(), ex.getMessage());
                salvarResultadoPagamento(tx, event, false);
            }
        });
    }

    private void despacharParaGateway(String tipo, Transaction tx) {
        switch (tipo) {
            case "PAYMENT_INITIATED" -> {
                String gwId = gatewayService.cobrar(tx);
                tx.setGatewayTransactionId(gwId);
            }
            case "PAYMENT_RELEASED"  -> { gatewayService.liberar(tx);    tx.liberar(); }
            case "PAYMENT_REFUNDED"  -> { gatewayService.reembolsar(tx); tx.reembolsar(); }
            default -> log.warn("Tipo de evento de pagamento desconhecido: {}", tipo);
        }
    }

    @Transactional
    protected void salvarResultadoPagamento(Transaction tx, OutboxEvent event, boolean sucesso) {
        if (sucesso) {
            transactionRepository.save(tx);
            event.marcarProcessado();
        } else {
            event.marcarFalha();
        }
        outboxRepository.save(event);
    }

    // Eventos não-financeiros (SOS, etc.): loga e marca processado sem chamar gateway externo
    private void processarOutro(OutboxEvent event) {
        if ("SOS_TRIGGERED".equals(event.getTipoEvento())) {
            log.warn("ALERTA SOS ATIVO — notificar admin: {}", event.getPayload());
        } else {
            log.info("Evento outbox não reconhecido: tipo={}", event.getTipoEvento());
        }
        marcarProcessado(event);
    }

    @Transactional
    protected void marcarProcessado(OutboxEvent event) {
        event.marcarProcessado();
        outboxRepository.save(event);
    }
}
