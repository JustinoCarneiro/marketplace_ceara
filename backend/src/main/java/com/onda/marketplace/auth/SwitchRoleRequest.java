package com.onda.marketplace.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code papel}: ROLE_CLIENT ou ROLE_PROVIDER. {@code refreshToken}: a sessão anterior, VÁLIDA e desta conta —
 * obrigatória (achado da revisão cruzada, 2026-10-05): sem exigi-la, um access token sozinho (até 15 min, nunca
 * revogado por troca de senha) bastava para emitir uma sessão de 30 dias, sobrevivendo à revogação de todas as
 * sessões que a troca de senha faz.
 */
public record SwitchRoleRequest(@NotBlank String papel, @NotBlank String refreshToken) {}
