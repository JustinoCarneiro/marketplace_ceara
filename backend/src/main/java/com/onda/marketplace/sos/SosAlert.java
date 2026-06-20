package com.onda.marketplace.sos;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sos_alerts")
public class SosAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "service_request_id")
    private UUID serviceRequestId;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SosAlertStatus status = SosAlertStatus.ATIVO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    protected SosAlert() {}

    public SosAlert(UUID userId, UUID serviceRequestId, BigDecimal latitude, BigDecimal longitude) {
        this.userId           = userId;
        this.serviceRequestId = serviceRequestId;
        this.latitude         = latitude;
        this.longitude        = longitude;
    }

    public UUID           getId()               { return id; }
    public UUID           getUserId()           { return userId; }
    public UUID           getServiceRequestId() { return serviceRequestId; }
    public BigDecimal     getLatitude()         { return latitude; }
    public BigDecimal     getLongitude()        { return longitude; }
    public SosAlertStatus getStatus()           { return status; }
    public Instant        getCriadoEm()         { return criadoEm; }

    public void resolver() {
        this.status      = SosAlertStatus.RESOLVIDO;
        this.resolvidoEm = Instant.now();
    }

    public void marcarFalsoAlarme() {
        this.status      = SosAlertStatus.FALSO_ALARME;
        this.resolvidoEm = Instant.now();
    }
}
