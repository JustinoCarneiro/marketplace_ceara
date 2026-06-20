package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop20ByStatusOrderByCriadoEmAsc(OutboxStatus status);

    // Visão admin da fila outbox (monitoramento/reprocessamento)
    List<OutboxEvent> findByStatus(OutboxStatus status);
}
