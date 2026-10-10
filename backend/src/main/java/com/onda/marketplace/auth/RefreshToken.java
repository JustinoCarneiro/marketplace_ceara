package com.onda.marketplace.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    // Contexto da sessão (V24): renovar não pode jogar quem trocou para prestador de volta ao papel principal.
    // Null = sessão anterior à migration.
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 30)
    private UserRole papel;

    // Impressão da senha vigente quando a sessão foi emitida (V28): o consumo recusa o token se a senha mudou desde então, o que fecha a
    // corrida entre a troca de senha e uma emissão em voo. Null = sessão anterior à migration (vale).
    @Column(name = "senha_fp", length = 32)
    private String senhaFp;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revogado = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this(user, tokenHash, expiresAt, null);
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, UserRole papel) {
        this.user      = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.papel     = papel;
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, UserRole papel, String senhaFp) {
        this(user, tokenHash, expiresAt, papel);
        this.senhaFp = senhaFp;
    }

    public boolean isValid() {
        return !revogado && Instant.now().isBefore(expiresAt);
    }

    public void revoke() { this.revogado = true; }

    public UUID    getId()         { return id; }
    public User    getUser()       { return user; }
    public UserRole getPapel()     { return papel; }
    public String  getTokenHash()  { return tokenHash; }
    public String  getSenhaFp()    { return senhaFp; }
    public boolean isRevogado()    { return revogado; }
    public Instant getExpiresAt()  { return expiresAt; }
}
