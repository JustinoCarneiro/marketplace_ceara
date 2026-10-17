package com.onda.marketplace.proposal;

import java.math.BigDecimal;
import java.time.Instant;

/** Dados necessários ao perfil público, obtidos por um join explícito em uma consulta. */
public record ProposalTiming(BigDecimal valor, ProposalStatus status, Instant horarioProposto,
                             Instant createdAt, Instant requestCreatedAt, Instant iniciadoEm) {}
