package com.onda.marketplace.payment;

import jakarta.validation.constraints.NotBlank;

public record InitiatePaymentRequest(
        @NotBlank String metodo,
        String cardToken,
        Integer parcelas,
        String bandeira
) {
    public InitiatePaymentRequest(String metodo) {
        this(metodo, null, 1, null);
    }
}
