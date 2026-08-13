package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Item do histórico do prestador (ProviderHistoryScreen) — pedidos que ele atendeu e que já
 * saíram de ACEITO/EM_ANDAMENTO (concluídos, cancelados ou em disputa). O atendimento
 * corrente continua exclusivo da tab "Em Andamento" (GET /service-requests/active).
 */
public record ProviderHistoryDto(
        UUID       id,
        String     categoria,
        String     status,
        Instant    updatedAt,
        String     clienteNome,
        BigDecimal valor
) {}
