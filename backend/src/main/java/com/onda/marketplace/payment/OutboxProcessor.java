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

    private final OutboxEventRepository    outboxRepository;
    private final TransactionRepository    transactionRepository;
    private final GatewayService           gatewayService;

    public OutboxProcessor(OutboxEventRepository outboxRepository,
                           TransactionRepository transactionRepository,
                           GatewayService gatewayService) {
        this.outboxRepository     = outboxRepository;
        this.transactionRepository = transactionRepository;
        this.gatewayService        = gatewayService;
    }

    /**
     * Processa eventos pendentes. Não tem @Transactional — chamada ao gateway NUNCA
     * pode participar de uma transação de banco (princípio Escrow/Saga do CLAUDE.md).
     * Cada evento é atualizado em transação própria e separada da chamada ao gateway.
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
                // Gateway chamado FORA de qualquer @Transactional
                String gwId = gatewayService.cobrar(tx);
                tx.setGatewayTransactionId(gwId);
                salvarTransacaoEEvento(tx, event, true);
            } catch (Exception ex) {
                log.warn("Falha ao cobrar gateway para tx={}: {}", tx.getId(), ex.getMessage());
                salvarTransacaoEEvento(tx, event, false);
            }
        });
    }

    // Transação separada apenas para persistir o resultado — sem o gateway dentro
    @Transactional
    protected void salvarTransacaoEEvento(Transaction tx, OutboxEvent event, boolean sucesso) {
        if (sucesso) {
            transactionRepository.save(tx);
            event.marcarProcessado();
        } else {
            event.marcarFalha();
        }
        outboxRepository.save(event);
    }
}
