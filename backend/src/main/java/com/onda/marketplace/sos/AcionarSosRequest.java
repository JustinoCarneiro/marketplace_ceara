package com.onda.marketplace.sos;

import java.math.BigDecimal;
import java.util.UUID;

public record AcionarSosRequest(
        UUID       serviceRequestId,
        BigDecimal latitude,
        BigDecimal longitude
) {}
