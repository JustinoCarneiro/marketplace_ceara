package com.onda.marketplace.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    Optional<Transaction> findByGatewayTransactionId(String gatewayTransactionId);
    Optional<Transaction> findByServiceRequestId(UUID serviceRequestId);

    // Reconciliação financeira (US27)
    java.util.List<Transaction> findByStatusPagamento(TransactionStatus statusPagamento);

    // Cobranças já despachadas ao gateway e ainda não confirmadas (gateway simulado em dev)
    java.util.List<Transaction> findByStatusPagamentoAndGatewayTransactionIdIsNotNull(
            TransactionStatus statusPagamento);

    // Receita de comissão do dashboard (US23): só conta o que efetivamente saiu do escrow
    @Query("SELECT COALESCE(SUM(t.valorComissao), 0) FROM Transaction t WHERE t.statusPagamento = :status")
    BigDecimal somaComissaoPorStatus(@Param("status") TransactionStatus status);

    // --- Métricas do dashboard com filtro de período (US23) ---

    // Período sempre vem preenchido (o service troca "sem filtro" por datas-sentinela):
    // "(:de IS NULL OR ...)" fazia o Postgres falhar com "could not determine data type
    // of parameter" — ele não infere tipo de parâmetro NULL solto. Faixa fechada também
    // usa índice melhor.

    @Query("""
           SELECT COALESCE(SUM(t.valorComissao), 0) FROM Transaction t
            WHERE t.statusPagamento = :status
              AND t.createdAt >= :de AND t.createdAt < :ate
           """)
    BigDecimal somaComissaoNoPeriodo(@Param("status") TransactionStatus status,
                                     @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("""
           SELECT COALESCE(SUM(t.valorTotal), 0) FROM Transaction t
            WHERE t.statusPagamento IN :statuses
              AND t.createdAt >= :de AND t.createdAt < :ate
           """)
    BigDecimal somaValorTotalNoPeriodo(@Param("statuses") java.util.Collection<TransactionStatus> statuses,
                                       @Param("de") Instant de, @Param("ate") Instant ate);

    @Query("""
           SELECT COUNT(t) FROM Transaction t
            WHERE t.statusPagamento IN :statuses
              AND t.createdAt >= :de AND t.createdAt < :ate
           """)
    long contarNoPeriodo(@Param("statuses") java.util.Collection<TransactionStatus> statuses,
                         @Param("de") Instant de, @Param("ate") Instant ate);
}
