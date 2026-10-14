package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop20ByStatusOrderByCriadoEmAsc(OutboxStatus status);

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
