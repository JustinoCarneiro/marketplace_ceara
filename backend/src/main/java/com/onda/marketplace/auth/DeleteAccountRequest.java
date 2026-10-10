package com.onda.marketplace.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * US36 — a senha atual confirma a exclusão: quem pegou um celular desbloqueado não apaga a conta alheia.
 * O teto de tamanho só limita a entrada; uma senha longa demais para existir cai no mesmo
 * {@code INVALID_PASSWORD} de qualquer senha errada.
 */
public record DeleteAccountRequest(@NotBlank @Size(max = 256) String senha) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[***]";
    }
}
