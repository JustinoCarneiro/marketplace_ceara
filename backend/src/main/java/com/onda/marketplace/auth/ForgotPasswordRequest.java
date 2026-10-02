package com.onda.marketplace.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** US35 — pedido de código de recuperação de senha. */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email
) {}
