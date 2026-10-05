package com.onda.marketplace.provider;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Quem já tem conta (cliente) passa a prestar serviço na mesma conta: não repete nome, e-mail nem senha. */
public record BecomeProviderRequest(
        @NotBlank String cpf,
        @NotBlank String categoria,
        String bio,
        @NotNull @AssertTrue Boolean aceitouTermos
) {}
