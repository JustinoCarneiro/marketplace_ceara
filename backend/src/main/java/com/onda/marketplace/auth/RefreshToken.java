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

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revogado = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user      = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isValid() {
        return !revogado && Instant.now().isBefore(expiresAt);
    }

    public void revoke() { this.revogado = true; }

    public User    getUser()       { return user; }
    public String  getTokenHash()  { return tokenHash; }
    public boolean isRevogado()    { return revogado; }
    public Instant getExpiresAt()  { return expiresAt; }
}
