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

    @Column(nullable = false)
    private boolean ativo = true;

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

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
