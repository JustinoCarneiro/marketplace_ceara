package com.onda.marketplace.payment;

import jakarta.validation.constraints.NotBlank;

public record InitiatePaymentRequest(@NotBlank String metodo) {}
