package com.onda.marketplace.payment;

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

    @Column(name = "idempotency_key", nullable = false, unique = true)
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

    public void reter()       { this.statusPagamento = TransactionStatus.RETIDO; }
    public void liberar()     { this.statusPagamento = TransactionStatus.LIBERADO; }
    public void reembolsar()  { this.statusPagamento = TransactionStatus.REEMBOLSADO; }
    public void setGatewayTransactionId(String v) { this.gatewayTransactionId = v; }
}
