package com.onda.marketplace.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

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
            processar(event);
        }
    }

    private void processar(OutboxEvent event) {
        transactionRepository.findById(event.getAgregadoId()).ifPresent(tx -> {
            try {
                despacharParaGateway(event.getTipoEvento(), tx);
                if ("PAYMENT_INITIATED".equals(event.getTipoEvento())) {
                    // gatewayId retornado por cobrar() já foi salvo inline; não precisa re-salvar
                }
                salvarResultado(tx, event, true);
            } catch (Exception ex) {
                log.warn("Falha ao processar evento {} para tx={}: {}", event.getTipoEvento(),
                        tx.getId(), ex.getMessage());
                salvarResultado(tx, event, false);
            }
        });
    }

    private void despacharParaGateway(String tipo, Transaction tx) {
        switch (tipo) {
            case "PAYMENT_INITIATED" -> {
                String gwId = gatewayService.cobrar(tx);
                tx.setGatewayTransactionId(gwId);
            }
            case "PAYMENT_RELEASED"  -> {
                gatewayService.liberar(tx);
                tx.liberar();
            }
            case "PAYMENT_REFUNDED"  -> {
                gatewayService.reembolsar(tx);
                tx.reembolsar();
            }
            default -> log.warn("Tipo de evento desconhecido no Outbox: {}", tipo);
        }
    }

    // Transação separada: persiste apenas o resultado — gateway já foi chamado fora
    @Transactional
    protected void salvarResultado(Transaction tx, OutboxEvent event, boolean sucesso) {
        if (sucesso) {
            transactionRepository.save(tx);
            event.marcarProcessado();
        } else {
            event.marcarFalha();
        }
        outboxRepository.save(event);
    }
}
