package com.onda.marketplace.admin;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro imutável (append-only) da resolução de uma disputa pela mediação.
 * Trilha de auditoria de ação administrativa que move dinheiro (TS09/US22).
 */
@Entity
@Table(name = "dispute_resolutions")
public class DisputeResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // UNIQUE: a disputa é terminal (EM_DISPUTA → CONCLUIDO|CANCELADO). Uma única resolução
    // por pedido. Sob concorrência, a 2ª inserção viola a constraint e faz rollback de tudo
    // (estado + outbox), garantindo idempotência do repasse/reembolso (princípio do CLAUDE.md).
    @Column(name = "service_request_id", nullable = false, unique = true)
    private UUID serviceRequestId;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediationDecision decisao;

    @Column(columnDefinition = "TEXT")
    private String justificativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected DisputeResolution() {}

    public DisputeResolution(UUID serviceRequestId, UUID adminId,
                             MediationDecision decisao, String justificativa) {
        this.serviceRequestId = serviceRequestId;
        this.adminId          = adminId;
        this.decisao          = decisao;
        this.justificativa    = justificativa;
    }

    public UUID getId()                   { return id; }
    public UUID getServiceRequestId()     { return serviceRequestId; }
    public UUID getAdminId()              { return adminId; }
    public MediationDecision getDecisao() { return decisao; }
    public String getJustificativa()      { return justificativa; }
    public Instant getCriadoEm()          { return criadoEm; }
}
