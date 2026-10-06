package com.onda.marketplace.provider;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Quem já tem conta (cliente) passa a prestar serviço na mesma conta: não repete nome, e-mail nem senha.
 * {@code refreshToken} (achado da revisão cruzada, 2026-10-05): obrigatório e desta conta — sem exigi-lo, um access
 * token sozinho bastava para emitir a sessão de prestador, pelo mesmo motivo do {@code switchRole}.
 */
public record BecomeProviderRequest(
        @NotBlank String cpf,
        @NotBlank String categoria,
        String bio,
        @NotNull @AssertTrue Boolean aceitouTermos,
        @NotBlank String refreshToken
) {}
