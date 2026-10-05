package com.onda.marketplace.auth;

import jakarta.validation.constraints.NotBlank;

/** {@code papel}: ROLE_CLIENT ou ROLE_PROVIDER. {@code refreshToken} (opcional): a sessão anterior, que passa a ser revogada. */
public record SwitchRoleRequest(@NotBlank String papel, String refreshToken) {}
