package com.onda.marketplace.payment;

public record WebhookPayload(String gatewayTransactionId, String status) {}
