package com.onda.marketplace.proposal;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateProposalRequest(
        @NotNull @Positive BigDecimal valor,
        @Min(1) int prazoDias,
        @NotNull @Future Instant horarioProposto
) {}
