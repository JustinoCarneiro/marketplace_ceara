package com.onda.marketplace.payment;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;
    private final String         webhookSecret;

    public PaymentController(PaymentService paymentService,
                             @Value("${marketplace.webhook.secret}") String webhookSecret) {
        this.paymentService = paymentService;
        this.webhookSecret  = webhookSecret;
    }

    @PostMapping("/service-requests/{id}/payment")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<TransactionDto> initiatePayment(
            @PathVariable UUID id,
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            Authentication auth) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UUID clienteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        TransactionDto dto = paymentService.initiate(id, request, idempotencyKey, clienteId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // C-1: gateway webhook valida segredo compartilhado para evitar forjamento de status
    @PostMapping("/payments/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody WebhookPayload payload,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret) {

        if (!constantTimeEquals(webhookSecret, secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        paymentService.confirmPayment(payload.gatewayTransactionId(), payload.status());
        return ResponseEntity.ok().build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
