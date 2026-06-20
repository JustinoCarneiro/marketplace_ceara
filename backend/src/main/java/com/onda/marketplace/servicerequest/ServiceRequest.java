package com.onda.marketplace.servicerequest;

import com.onda.marketplace.auth.User;
import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_requests")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private User cliente;

    @Column(nullable = false)
    private String categoria;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceRequestStatus status = ServiceRequestStatus.PENDENTE;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point localizacao;

    @Column(name = "ai_descricao_sugerida", columnDefinition = "TEXT")
    private String aiDescricaoSugerida;

    @Column(name = "ai_faixa_min")
    private BigDecimal aiFaixaMin;

    @Column(name = "ai_faixa_max")
    private BigDecimal aiFaixaMax;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public UUID getId()                         { return id; }
    public User getCliente()                    { return cliente; }
    public String getCategoria()                { return categoria; }
    public String getDescricao()                { return descricao; }
    public ServiceRequestStatus getStatus()     { return status; }
    public Point getLocalizacao()               { return localizacao; }
    public String getAiDescricaoSugerida()      { return aiDescricaoSugerida; }
    public BigDecimal getAiFaixaMin()           { return aiFaixaMin; }
    public BigDecimal getAiFaixaMax()           { return aiFaixaMax; }
    public String getIdempotencyKey()           { return idempotencyKey; }
    public Instant getCreatedAt()               { return createdAt; }

    public void setCategoria(String v)          { this.categoria = v; }
    public void setDescricao(String v)          { this.descricao = v; }
    public void setStatus(ServiceRequestStatus v) { this.status = v; }
    public void setLocalizacao(Point v)         { this.localizacao = v; }
    public void setCliente(User v)              { this.cliente = v; }
    public void setIdempotencyKey(String v)     { this.idempotencyKey = v; }

    public void aplicarSugestaoIA(AiSuggestion s) {
        this.aiDescricaoSugerida = s.descricaoSugerida();
        this.aiFaixaMin          = s.faixaMin();
        this.aiFaixaMax          = s.faixaMax();
    }
}
