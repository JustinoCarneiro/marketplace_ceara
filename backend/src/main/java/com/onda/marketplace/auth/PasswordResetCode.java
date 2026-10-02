package com.onda.marketplace.auth;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Código de recuperação de senha (US35). Guarda só o HMAC do código — o texto que o usuário
 * recebe por e-mail não existe no banco. Vale uma vez, por tempo limitado e com número limitado
 * de tentativas erradas.
 */
@Entity
@Table(name = "password_reset_codes")
public class PasswordResetCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, updatable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PasswordResetCode() {}

    public PasswordResetCode(UUID userId, String codeHash, Instant expiresAt) {
        this.userId    = userId;
        this.codeHash  = codeHash;
        this.expiresAt = expiresAt;
    }

    /** Aberto, dentro da validade e ainda com tentativas — a única situação em que serve. */
    public boolean isAtivo(Instant agora, int maxTentativas) {
        return closedAt == null && agora.isBefore(expiresAt) && attempts < maxTentativas;
    }

    /** Conta um erro; ao chegar no limite o código se fecha e é preciso pedir outro. */
    public void registrarErro(int maxTentativas, Instant agora) {
        attempts++;
        if (attempts >= maxTentativas) {
            fechar(agora);
        }
    }

    /** Encerra o código (usado, substituído ou esgotado). Idempotente: mantém o 1º instante. */
    public void fechar(Instant agora) {
        if (closedAt == null) {
            closedAt = agora;
        }
    }

    public UUID    getId()        { return id; }
    public UUID    getUserId()    { return userId; }
    public String  getCodeHash()  { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int     getAttempts()  { return attempts; }
    public Instant getClosedAt()  { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
