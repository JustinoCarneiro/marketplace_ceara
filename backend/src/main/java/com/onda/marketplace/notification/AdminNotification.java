package com.onda.marketplace.notification;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Alerta operacional persistido na tabela {@code admin_notifications} (M12/US30).
 * Tipos: {@code SOS}, {@code DISPUTA}, {@code VERIFICACAO}.
 * Nunca armazena CPF ou dado pessoal — somente o UUID do registro de origem (TS04/LGPD).
 */
@Entity
@Table(name = "admin_notifications")
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(name = "ref_id", nullable = false)
    private UUID refId;

    @Column(nullable = false)
    private boolean lida = false;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected AdminNotification() {}

    public AdminNotification(String tipo, UUID refId) {
        this.tipo  = tipo;
        this.refId = refId;
    }

    public void marcarLida() {
        this.lida = true;
    }

    // --- getters ---
    public UUID    getId()      { return id;       }
    public String  getTipo()    { return tipo;     }
    public UUID    getRefId()   { return refId;    }
    public boolean isLida()     { return lida;     }
    public Instant getCriadoEm(){ return criadoEm; }
}
