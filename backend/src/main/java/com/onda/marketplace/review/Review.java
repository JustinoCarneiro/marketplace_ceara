package com.onda.marketplace.review;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews",
       uniqueConstraints = @UniqueConstraint(columnNames = {"service_request_id", "tipo"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_request_id", nullable = false)
    private UUID serviceRequestId;

    @Column(name = "avaliador_id", nullable = false)
    private UUID avaliadorId;

    @Column(name = "avaliado_id", nullable = false)
    private UUID avaliadoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewType tipo;

    @Column(nullable = false)
    private int nota;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    /**
     * Double-blind: nasce oculta e só é revelada quando a contraparte também
     * avalia, ou quando expira o prazo de reciprocidade (job agendado).
     * Enquanto oculta, não entra na nota média nem no perfil público.
     */
    @Column(nullable = false)
    private boolean revelada = false;

    @Column(name = "revelada_em")
    private Instant reveladaEm;

    protected Review() {}

    public Review(UUID serviceRequestId, UUID avaliadorId, UUID avaliadoId,
                  ReviewType tipo, int nota, String comentario) {
        this.serviceRequestId = serviceRequestId;
        this.avaliadorId      = avaliadorId;
        this.avaliadoId       = avaliadoId;
        this.tipo             = tipo;
        this.nota             = nota;
        this.comentario       = comentario;
    }

    public UUID       getId()               { return id; }
    public UUID       getServiceRequestId() { return serviceRequestId; }
    public UUID       getAvaliadorId()      { return avaliadorId; }
    public UUID       getAvaliadoId()       { return avaliadoId; }
    public ReviewType getTipo()             { return tipo; }
    public int        getNota()             { return nota; }
    public String     getComentario()       { return comentario; }
    public Instant    getCriadoEm()         { return criadoEm; }
    public boolean    isRevelada()          { return revelada; }
    public Instant    getReveladaEm()       { return reveladaEm; }

    /** Idempotente: revelar uma avaliação já revelada não altera a data original. */
    public void revelar() {
        if (!this.revelada) {
            this.revelada   = true;
            this.reveladaEm = Instant.now();
        }
    }
}
