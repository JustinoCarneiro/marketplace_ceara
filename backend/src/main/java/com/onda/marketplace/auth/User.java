package com.onda.marketplace.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // HMAC-SHA256 do CPF — indexado para unique constraint (LGPD: CPF em claro nunca armazenado)
    @Column(name = "cpf_hash", unique = true)
    private String cpfHash;

    @Column(nullable = false)
    private boolean ativo = true;

    // Exclusão de conta (US36): a linha fica — outras tabelas apontam para ela e o aceite de termos é
    // imutável —, mas sem dado pessoal. Preenchido = conta excluída.
    @Column(name = "excluido_em")
    private Instant excluidoEm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected User() {}

    private User(Builder b) {
        this.nome      = b.nome;
        this.email     = b.email;
        this.senhaHash = b.senhaHash;
        this.role      = b.role;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String nome;
        private String email;
        private String senhaHash;
        private UserRole role;

        public Builder nome(String v)       { this.nome = v;       return this; }
        public Builder email(String v)      { this.email = v;      return this; }
        public Builder senhaHash(String v)  { this.senhaHash = v;  return this; }
        public Builder role(UserRole v)     { this.role = v;       return this; }
        public User build()                 { return new User(this); }
    }

    public UUID     getId()        { return id; }
    public String   getNome()      { return nome; }
    public String   getEmail()     { return email; }
    public String   getSenhaHash() { return senhaHash; }
    public UserRole getRole()      { return role; }
    public boolean  isAtivo()      { return ativo; }
    public String   getCpfHash()   { return cpfHash; }

    public void setCpfHash(String hash) { this.cpfHash = hash; }

    /** Troca a senha (US35). Recebe o hash já calculado — a senha em claro nunca entra aqui. */
    public void trocarSenha(String novoSenhaHash) { this.senhaHash = novoSenhaHash; }

    /** Suspende o acesso do usuário (US26 — gestão pelo admin). */
    public void suspender() { this.ativo = false; }

    /** Reativa o acesso do usuário (US26). Conta excluída (US36) nunca volta: não há mais dado a reativar. */
    public void reativar() {
        if (isExcluido()) {
            throw new IllegalStateException("Conta excluída não pode ser reativada.");
        }
        this.ativo = true;
    }

    public boolean isExcluido()    { return excluidoEm != null; }
    public Instant getExcluidoEm() { return excluidoEm; }

    /**
     * Exclusão de conta (US36): troca tudo que identifica a pessoa e bloqueia o acesso. Recebe o e-mail
     * anônimo e o hash da senha inutilizada já prontos (a senha em claro nunca entra aqui).
     * {@code manterCpfHash}: antifraude — conta suspensa/prestador reprovado mantém o hash do CPF para
     * que excluir a conta não seja um jeito de burlar o banimento. Chamar de novo não refaz nada.
     */
    public void anonimizar(String emailAnonimo, String senhaHashInutilizada, boolean manterCpfHash) {
        if (isExcluido()) {
            return;
        }
        this.nome      = "Usuário removido";
        this.email     = emailAnonimo;
        this.senhaHash = senhaHashInutilizada;
        if (!manterCpfHash) {
            this.cpfHash = null;
        }
        this.ativo      = false;
        this.excluidoEm = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
