package com.onda.marketplace.admin;

import com.onda.marketplace.payment.OutboxStatus;

import java.util.UUID;

/** Visão de evento outbox para monitoramento e reprocessamento no painel admin. */
public record OutboxAdminDto(
        UUID id,
        String agregado,
        String tipoEvento,
        int tentativas,
        OutboxStatus status
) {}
