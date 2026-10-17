package com.onda.marketplace.provider;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Guarda somente estado operacional e códigos fechados; nunca laudos ou CPF. */
@Entity
@Table(name = "background_checks")
public class BackgroundCheck {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(nullable = false) private String status = "PENDENTE";
    private String resultado;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt = Instant.now();
    @Column(name = "completed_at") private Instant completedAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt = Instant.now();
    @Column(name = "claim_token") private UUID claimToken;

    protected BackgroundCheck() {}
    BackgroundCheck(UUID providerId) { this.providerId = providerId; }
    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public String getStatus() { return status; }
    public String getResultado() { return resultado; }
    public int getAttempts() { return attempts; }
    boolean eligible(Instant now) {
        return (status.equals("PENDENTE") || status.equals("PROCESSANDO")) && !availableAt.isAfter(now);
    }
    UUID claim(Instant now) {
        status = "PROCESSANDO";
        attempts++;
        claimToken = UUID.randomUUID();
        availableAt = now.plusSeconds(120);
        return claimToken;
    }
    boolean owns(UUID token) { return status.equals("PROCESSANDO") && token.equals(claimToken); }
    void retry(Instant now) {
        status = "PENDENTE";
        claimToken = null;
        availableAt = now.plusSeconds(60L * attempts);
    }
    void complete(String status, String reason, Instant now) {
        this.status = status;
        resultado = reason;
        completedAt = now;
        claimToken = null;
    }
}
