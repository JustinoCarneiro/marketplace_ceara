package com.onda.marketplace.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyIdentityRequest(
        @NotBlank
        @Pattern(regexp = "\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}",
                 message = "CPF inválido")
        String cpf
) {}
