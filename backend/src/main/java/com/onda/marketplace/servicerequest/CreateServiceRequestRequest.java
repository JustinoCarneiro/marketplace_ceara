package com.onda.marketplace.servicerequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateServiceRequestRequest(
        @NotBlank String  categoria,
        String            descricao,
        @NotNull Double   lat,
        @NotNull Double   lng,
        @Size(max = 60) String bairro   // service_requests.bairro é VARCHAR(60) (V17)
) {}
