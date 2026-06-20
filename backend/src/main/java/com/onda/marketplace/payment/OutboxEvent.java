package com.onda.marketplace.payment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String agregado;

    @Column(name = "agregado_id", nullable = false)
    private UUID agregadoId;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    // JSONB no Postgres — mapeado como TEXT para compatibilidade com H2 em testes
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDENTE;

    @Column(nullable = false)
    private int tentativas = 0;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "processado_em")
    private Instant processadoEm;

    protected OutboxEvent() {}

    public OutboxEvent(String agregado, UUID agregadoId, String tipoEvento, String payload) {
        this.agregado   = agregado;
        this.agregadoId = agregadoId;
        this.tipoEvento = tipoEvento;
        this.payload    = payload;
    }

    public UUID getId()              { return id; }
    public String getAgregado()      { return agregado; }
    public UUID getAgregadoId()      { return agregadoId; }
    public String getTipoEvento()    { return tipoEvento; }
    public String getPayload()       { return payload; }
    public OutboxStatus getStatus()  { return status; }
    public int getTentativas()       { return tentativas; }
    public Instant getCriadoEm()     { return criadoEm; }

    public void marcarProcessado() {
        this.status       = OutboxStatus.PROCESSADO;
        this.processadoEm = Instant.now();
    }

    public void marcarFalha() {
        this.tentativas++;
        this.status = OutboxStatus.FALHA;
    }

    public void resetarParaRetry() {
        this.status = OutboxStatus.PENDENTE;
    }
}
