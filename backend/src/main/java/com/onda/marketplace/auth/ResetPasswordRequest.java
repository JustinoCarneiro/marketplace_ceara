package com.onda.marketplace.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * US35 — confirmação da recuperação: e-mail, código recebido e a nova senha. A senha segue a
 * regra do cadastro (mínimo 8). O teto é de caracteres; o de BYTES (72, limite do BCrypt) é
 * conferido no serviço, porque "ç" ocupa 2 bytes.
 */
public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 32) String codigo,
        @NotBlank @Size(min = 8, max = 72) String novaSenha
) {}
