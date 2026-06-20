package com.onda.marketplace.proposal;

import com.onda.marketplace.servicerequest.ServiceRequest;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "proposals")
public class Proposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    // FK armazenada como UUID para evitar carregamento do User (consultado via JWT)
    @Column(name = "prestador_id", nullable = false)
    private UUID prestadorId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "prazo_dias", nullable = false)
    private int prazoDias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Proposal() {}

    public Proposal(ServiceRequest serviceRequest, UUID prestadorId, BigDecimal valor, int prazoDias, ProposalStatus status) {
        this.serviceRequest = serviceRequest;
        this.prestadorId    = prestadorId;
        this.valor          = valor;
        this.prazoDias      = prazoDias;
        this.status         = status;
    }

    public UUID getId()                     { return id; }
    public ServiceRequest getServiceRequest() { return serviceRequest; }
    public UUID getPrestadorId()            { return prestadorId; }
    public BigDecimal getValor()            { return valor; }
    public int getPrazoDias()               { return prazoDias; }
    public ProposalStatus getStatus()       { return status; }
    public Instant getCreatedAt()           { return createdAt; }

    public void aceitar()   { this.status = ProposalStatus.ACEITA; }
    public void recusar()   { this.status = ProposalStatus.RECUSADA; }
    public void encerrar()  { this.status = ProposalStatus.ENCERRADA; }
}
