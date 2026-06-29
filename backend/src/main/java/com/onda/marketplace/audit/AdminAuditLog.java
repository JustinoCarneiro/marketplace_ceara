package com.onda.marketplace.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro imutável de uma ação administrativa (US22 / TS09). Tabela
 * {@code admin_audit_log} é <b>append-only</b>: sem setters e sem updates —
 * a trilha nunca é alterada depois de gravada.
 *
 * <p>O nome do admin é um <b>snapshot</b> do momento da ação (não depende de o
 * usuário continuar existindo/inalterado).
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "admin_nome", nullable = false)
    private String adminNome;

    @Column(nullable = false, length = 60)
    private String acao;

    @Column(nullable = false, length = 60)
    private String entidade;

    @Column(name = "entidade_id")
    private UUID entidadeId;

    @Column
    private String detalhe;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    protected AdminAuditLog() {}

    public AdminAuditLog(UUID adminId, String adminNome, String acao,
                         String entidade, UUID entidadeId, String detalhe) {
        this.adminId    = adminId;
        this.adminNome  = adminNome;
        this.acao       = acao;
        this.entidade   = entidade;
        this.entidadeId = entidadeId;
        this.detalhe    = detalhe;
    }

    public UUID    getId()         { return id;         }
    public UUID    getAdminId()    { return adminId;    }
    public String  getAdminNome()  { return adminNome;  }
    public String  getAcao()       { return acao;       }
    public String  getEntidade()   { return entidade;   }
    public UUID    getEntidadeId() { return entidadeId; }
    public String  getDetalhe()    { return detalhe;    }
    public Instant getCriadoEm()   { return criadoEm;   }
}
