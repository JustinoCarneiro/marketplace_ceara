package com.onda.marketplace.admin;

import com.onda.marketplace.payment.OutboxStatus;

import java.util.UUID;

/**
 * Visão de evento outbox para monitoramento e reprocessamento no painel admin.
 * {@code agregadoId} (ex.: o id da Transaction em eventos de pagamento) é o que o
 * admin usa pra ir à fila de repasse manual (MKT-49) num evento PAYMENT_RELEASED.
 */
public record OutboxAdminDto(
        UUID id,
        String agregado,
        UUID agregadoId,
        String tipoEvento,
        int tentativas,
        OutboxStatus status
) {}
