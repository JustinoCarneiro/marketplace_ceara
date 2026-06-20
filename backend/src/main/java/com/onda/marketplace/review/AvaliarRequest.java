package com.onda.marketplace.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AvaliarRequest(
        @Min(1) @Max(5) int nota,
        String comentario
) {}
