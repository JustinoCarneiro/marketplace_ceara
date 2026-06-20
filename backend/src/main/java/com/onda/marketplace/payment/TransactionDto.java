package com.onda.marketplace.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionDto(
        UUID       id,
        UUID       serviceRequestId,
        BigDecimal valorTotal,
        String     metodo,
        String     statusPagamento,
        Instant    createdAt
) {
    static TransactionDto from(Transaction t) {
        return new TransactionDto(
                t.getId(), t.getServiceRequestId(), t.getValorTotal(),
                t.getMetodo().name(), t.getStatusPagamento().name(), t.getCreatedAt());
    }
}
