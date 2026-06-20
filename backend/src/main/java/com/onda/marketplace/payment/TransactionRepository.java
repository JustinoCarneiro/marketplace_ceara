package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    Optional<Transaction> findByGatewayTransactionId(String gatewayTransactionId);
    Optional<Transaction> findByServiceRequestId(UUID serviceRequestId);

    // Receita de comissão do dashboard (US23): só conta o que efetivamente saiu do escrow
    @Query("SELECT COALESCE(SUM(t.valorComissao), 0) FROM Transaction t WHERE t.statusPagamento = :status")
    BigDecimal somaComissaoPorStatus(@Param("status") TransactionStatus status);
}
