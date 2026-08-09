package com.onda.marketplace.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistência dos resultados de processamento do Outbox, isolada em bean próprio.
 *
 * <p>{@code @Transactional} só é aplicado pelo proxy do Spring em chamadas vindas de FORA
 * do bean — {@link OutboxProcessor} chamando estes métodos via {@code this.} (mesma classe)
 * bypassaria o proxy e a transação nunca existiria de fato. Por isso os métodos moram aqui,
 * num componente separado injetado no processor.
 */
@Component
@SuppressWarnings("null")
public class OutboxEventWriter {

    private final OutboxEventRepository outboxRepository;
    private final TransactionRepository transactionRepository;

    public OutboxEventWriter(OutboxEventRepository outboxRepository,
                             TransactionRepository transactionRepository) {
        this.outboxRepository      = outboxRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void salvarResultadoPagamento(Transaction tx, OutboxEvent event, boolean sucesso) {
        if (sucesso) {
            transactionRepository.save(tx);
            event.marcarProcessado();
        } else {
            event.marcarFalha();
        }
        outboxRepository.save(event);
    }

    @Transactional
    public void salvarFalha(OutboxEvent event) {
        event.marcarFalha();
        outboxRepository.save(event);
    }

    @Transactional
    public void marcarProcessado(OutboxEvent event) {
        event.marcarProcessado();
        outboxRepository.save(event);
    }
}
