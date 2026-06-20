package com.onda.marketplace.provider;

import com.onda.marketplace.auth.User;
import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "providers_profile")
public class ProviderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String categoria;

    private String bio;

    @Column(name = "cpf_cifrado")
    private String cpfCifrado;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point localizacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_verificacao", nullable = false)
    private ProviderStatus statusVerificacao = ProviderStatus.EM_VERIFICACAO;

    @Column(name = "saldo_retido", nullable = false)
    private BigDecimal saldoRetido = BigDecimal.ZERO;

    @Column(name = "nota_media")
    private BigDecimal notaMedia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    protected ProviderProfile() {}

    public ProviderProfile(User user, String categoria, String cpfCifrado) {
        this.user         = user;
        this.categoria    = categoria;
        this.cpfCifrado   = cpfCifrado;
    }

    public UUID getId()                    { return id; }
    public User getUser()                  { return user; }
    public String getCategoria()           { return categoria; }
    public String getCpfCifrado()          { return cpfCifrado; }
    public ProviderStatus getStatusVerificacao() { return statusVerificacao; }
    public BigDecimal getSaldoRetido()     { return saldoRetido; }
    public BigDecimal getNotaMedia()       { return notaMedia; }
    public Instant getCreatedAt()          { return createdAt; }

    public Point getLocalizacao()          { return localizacao; }
    public void  setLocalizacao(Point p)   { this.localizacao = p; }

    public void aprovar()   { this.statusVerificacao = ProviderStatus.VERIFICADO; }
    public void reprovar()  { this.statusVerificacao = ProviderStatus.REPROVADO; }
}
