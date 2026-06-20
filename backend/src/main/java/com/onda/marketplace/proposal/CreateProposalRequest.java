package com.onda.marketplace.proposal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProposalRequest(
        @NotNull @Positive BigDecimal valor,
        @Min(1) int prazoDias
) {}
