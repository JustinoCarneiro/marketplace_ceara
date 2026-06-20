package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    Optional<Transaction> findByGatewayTransactionId(String gatewayTransactionId);
    Optional<Transaction> findByServiceRequestId(UUID serviceRequestId);
}
