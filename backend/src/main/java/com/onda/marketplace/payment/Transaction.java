package com.onda.marketplace.payment;

import com.onda.marketplace.shared.exception.BusinessException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FK como UUID para evitar JOIN desnecessário; unicidade garantida pela constraint UNIQUE no banco
    @Column(name = "service_request_id", nullable = false, unique = true)
    private UUID serviceRequestId;

    @Column(name = "valor_total", nullable = false)
    private BigDecimal valorTotal;

    @Column(name = "valor_comissao", nullable = false)
    private BigDecimal valorComissao;

    @Column(name = "percentual_comissao", nullable = false)
    private BigDecimal percentualComissao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod metodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_pagamento", nullable = false)
    private TransactionStatus statusPagamento = TransactionStatus.PENDENTE;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    protected Transaction() {}

    public Transaction(UUID serviceRequestId, BigDecimal valorTotal, BigDecimal valorComissao,
                       BigDecimal percentualComissao, PaymentMethod metodo, String idempotencyKey) {
        this.serviceRequestId  = serviceRequestId;
        this.valorTotal        = valorTotal;
        this.valorComissao     = valorComissao;
        this.percentualComissao = percentualComissao;
        this.metodo            = metodo;
        this.idempotencyKey    = idempotencyKey;
    }

    public UUID getId()                      { return id; }
    public UUID getServiceRequestId()        { return serviceRequestId; }
    public BigDecimal getValorTotal()        { return valorTotal; }
    public BigDecimal getValorComissao()     { return valorComissao; }
    public BigDecimal getPercentualComissao() { return percentualComissao; }
    public PaymentMethod getMetodo()         { return metodo; }
    public TransactionStatus getStatusPagamento() { return statusPagamento; }
    public String getGatewayTransactionId()  { return gatewayTransactionId; }
    public String getIdempotencyKey()        { return idempotencyKey; }
    public Instant getCreatedAt()            { return createdAt; }

    /*
     * Máquina de estados financeira (CLAUDE.md):
     *     PENDENTE → RETIDO → (LIBERADO | REEMBOLSADO)
     *
     * As transições são guardadas aqui, na entidade, e não só em quem chama: sem isso
     * um PAYMENT_RELEASED sobre transação PENDENTE repassaria ao prestador dinheiro que
     * nunca foi cobrado do cliente. Repetir a transição atual é no-op de propósito —
     * webhook de gateway é reentregue e o OutboxProcessor faz retry; ambos precisam ser
     * idempotentes. Já andar para trás (ex.: reter depois de liberado) é anomalia real
     * e estoura.
     */

    public void reter() {
        if (statusPagamento == TransactionStatus.RETIDO) return;   // reentrega do webhook
        exigir(TransactionStatus.PENDENTE, TransactionStatus.RETIDO);
        this.statusPagamento = TransactionStatus.RETIDO;
    }

    public void liberar() {
        if (statusPagamento == TransactionStatus.LIBERADO) return; // retry do outbox
        exigir(TransactionStatus.RETIDO, TransactionStatus.LIBERADO);
        this.statusPagamento = TransactionStatus.LIBERADO;
    }

    public void reembolsar() {
        if (statusPagamento == TransactionStatus.REEMBOLSADO) return;
        exigir(TransactionStatus.RETIDO, TransactionStatus.REEMBOLSADO);
        this.statusPagamento = TransactionStatus.REEMBOLSADO;
    }

    private void exigir(TransactionStatus origemExigida, TransactionStatus destino) {
        if (statusPagamento != origemExigida) {
            throw new BusinessException("INVALID_PAYMENT_TRANSITION",
                    "Transição financeira inválida: " + statusPagamento + " → " + destino
                            + " (exige " + origemExigida + ").");
        }
    }

    public void setGatewayTransactionId(String v) { this.gatewayTransactionId = v; }
}
