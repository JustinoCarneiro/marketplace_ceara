package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
