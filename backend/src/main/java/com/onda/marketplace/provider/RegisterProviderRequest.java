package com.onda.marketplace.provider;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterProviderRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String senha,
        @NotBlank String cpf,
        @NotBlank String categoria,
        String bio,
        @NotNull @AssertTrue Boolean aceitouTermos
) {}
