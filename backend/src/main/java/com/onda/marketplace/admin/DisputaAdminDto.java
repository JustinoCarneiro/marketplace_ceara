package com.onda.marketplace.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Visão resumida de um pedido em disputa para a fila do painel admin (US24). */
public record DisputaAdminDto(
        UUID serviceRequestId,
        String categoria,
        BigDecimal valorRetido,
        Instant criadoEm
) {}
