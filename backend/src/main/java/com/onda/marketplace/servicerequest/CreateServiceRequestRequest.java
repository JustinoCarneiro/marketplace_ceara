package com.onda.marketplace.servicerequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateServiceRequestRequest(
        @NotBlank String  categoria,
        String            descricao,
        @NotNull Double   lat,
        @NotNull Double   lng
) {}
