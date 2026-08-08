package com.onda.marketplace.denuncia;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Denúncia de prestador ou avaliação fraudulenta (canal de denúncia, Épico 7/9).
 * Nasce ABERTA; só um admin resolve (RESOLVIDA) — não é o denunciante quem fecha.
 */
@Entity
@Table(name = "denuncias")
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDenuncia tipo;

    // PRESTADOR: userId do prestador denunciado. AVALIACAO: id da Review denunciada.
    @Column(name = "alvo_id", nullable = false)
    private UUID alvoId;

    @Column(name = "denunciante_id", nullable = false)
    private UUID denuncianteId;

    @Column(nullable = false, length = 60)
    private String motivo;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusDenuncia status = StatusDenuncia.ABERTA;

    @Column(name = "resolvido_por_id")
    private UUID resolvidoPorId;

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected Denuncia() {}

    public Denuncia(TipoDenuncia tipo, UUID alvoId, UUID denuncianteId, String motivo, String detalhes) {
        this.tipo          = tipo;
        this.alvoId        = alvoId;
        this.denuncianteId = denuncianteId;
        this.motivo        = motivo;
        this.detalhes      = detalhes;
    }

    public void resolver(UUID adminId) {
        this.status         = StatusDenuncia.RESOLVIDA;
        this.resolvidoPorId = adminId;
        this.resolvidoEm    = Instant.now();
    }

    public UUID getId()               { return id; }
    public TipoDenuncia getTipo()     { return tipo; }
    public UUID getAlvoId()           { return alvoId; }
    public UUID getDenuncianteId()    { return denuncianteId; }
    public String getMotivo()         { return motivo; }
    public String getDetalhes()       { return detalhes; }
    public StatusDenuncia getStatus() { return status; }
    public UUID getResolvidoPorId()   { return resolvidoPorId; }
    public Instant getResolvidoEm()   { return resolvidoEm; }
    public Instant getCriadoEm()      { return criadoEm; }
}
