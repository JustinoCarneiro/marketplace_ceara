package com.onda.marketplace.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Prova de consentimento informado no cadastro (docs/PENDENCIAS_JURIDICAS.md item 3):
 * quem aceitou, qual versão do documento, quando e de onde. Tabela {@code terms_acceptance}
 * é append-only — sem setters, nunca é alterada depois de gravada.
 */
@Entity
@Table(name = "terms_acceptance")
public class TermsAcceptance {

    // Versão do rascunho em mobile/src/screens/legal/LegalScreen.tsx — atualizar aqui
    // sempre que o conteúdo de Termos/Privacidade mudar (docs/PENDENCIAS_JURIDICAS.md).
    public static final String CURRENT_DOC_VERSION = "v1-rascunho";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "doc_version", nullable = false, length = 30)
    private String docVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt = Instant.now();

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    protected TermsAcceptance() {}

    public TermsAcceptance(UUID userId, String docVersion, String ipAddress) {
        this.userId     = userId;
        this.docVersion = docVersion;
        this.ipAddress  = ipAddress;
    }

    public UUID    getId()         { return id; }
    public UUID    getUserId()     { return userId; }
    public String  getDocVersion() { return docVersion; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public String  getIpAddress()  { return ipAddress; }
}
