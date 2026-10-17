package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    /** Trava primeiro a transação financeira; outro nó pula a linha ocupada. */
    @Query(value = """
            SELECT e.id FROM outbox_events e JOIN transactions t ON t.id=e.agregado_id
             WHERE e.agregado='transaction' AND e.status='PENDENTE'
               AND e.tipo_evento IN ('PAYMENT_INITIATED','PAYMENT_RELEASED','PAYMENT_REFUNDED')
               AND NOT EXISTS (SELECT 1 FROM outbox_events busy
                    WHERE busy.agregado=e.agregado AND busy.agregado_id=e.agregado_id
                      AND busy.status='PROCESSANDO')
             ORDER BY e.criado_em,e.id LIMIT 1 FOR UPDATE OF t SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> candidatoFinanceiroComTrava();

    @Query(value = """
            SELECT e.* FROM outbox_events e
             WHERE (e.status='PENDENTE' OR (e.status='PROCESSANDO' AND e.tipo_evento='SOS_TRIGGERED'
                    AND e.agregado<>'transaction' AND e.processamento_em < CURRENT_TIMESTAMP-INTERVAL '5 minutes'))
               AND (e.tipo_evento NOT IN ('PAYMENT_INITIATED','PAYMENT_RELEASED','PAYMENT_REFUNDED')
                    OR e.agregado<>'transaction'
                    OR NOT EXISTS (SELECT 1 FROM transactions t WHERE t.id=e.agregado_id))
               AND NOT EXISTS (SELECT 1 FROM outbox_events busy
                    WHERE busy.agregado=e.agregado AND busy.agregado_id=e.agregado_id
                      AND busy.status='PROCESSANDO' AND busy.id<>e.id)
             ORDER BY CASE WHEN e.tipo_evento='SOS_TRIGGERED' THEN 0 ELSE 1 END,e.criado_em,e.id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> candidatoNaoFinanceiroComTrava();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.id=:id")
    Optional<OutboxEvent> findByIdComTrava(@Param("id") UUID id);

    boolean existsByAgregadoAndAgregadoIdAndStatus(String agregado, UUID agregadoId, OutboxStatus status);

    @Query("SELECT e.agregadoId FROM OutboxEvent e WHERE e.id=:id AND e.agregado='transaction'")
    Optional<UUID> findTransactionIdByEventId(@Param("id") UUID id);

    // Visão admin da fila outbox (monitoramento/reprocessamento)
    List<OutboxEvent> findByStatus(OutboxStatus status);

    // Fila de repasse manual (MKT-49): localiza o(s) evento(s) PAYMENT_RELEASED de uma
    // transação pra marcar como PROCESSADO quando o admin confirma o pagamento feito
    // fora do sistema — sem isso "reprocessar" chamaria o gateway de novo e falharia de novo.
    List<OutboxEvent> findByAgregadoIdAndTipoEvento(UUID agregadoId, String tipoEvento);

    /** Repasses que ainda exigem ação, contados por transação mesmo com vários eventos. */
    @Query("""
           SELECT COUNT(DISTINCT t.id) FROM OutboxEvent e, Transaction t
            WHERE e.agregado = 'transaction' AND e.agregadoId = t.id
              AND e.tipoEvento = 'PAYMENT_RELEASED'
              AND e.status <> com.onda.marketplace.payment.OutboxStatus.PROCESSADO
              AND e.criadoEm <= :limite
              AND t.statusPagamento = com.onda.marketplace.payment.TransactionStatus.RETIDO
           """)
    long contarRepassesPendentesAntesDe(@Param("limite") Instant limite);
}
