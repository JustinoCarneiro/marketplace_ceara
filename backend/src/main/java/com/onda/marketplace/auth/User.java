package com.onda.marketplace.auth;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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

    // Papel PRINCIPAL (o do cadastro): é o contexto em que o login abre. Os papéis que a conta TEM ficam em `papeis`; o papel
    // em uso numa sessão vem no token (claim "role") e é trocado por /auth/switch-role.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    // Conta única com papéis (V24): a mesma pessoa é cliente e prestador na MESMA conta. Todo prestador também contrata.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_papeis", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "papel", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<UserRole> papeis = new HashSet<>();

    // HMAC-SHA256 do CPF — indexado para unique constraint (LGPD: CPF em claro nunca armazenado)
    @Column(name = "cpf_hash", unique = true)
    private String cpfHash;

    // Versão da chave do HMAC com que o hash foi calculado (V25): permite trocar a chave sem perder a unicidade.
    @Column(name = "cpf_hash_versao", nullable = false)
    private int cpfHashVersao = 1;

    @Column(nullable = false)
    private boolean ativo = true;

    // Exclusão de conta (US36): a linha fica — outras tabelas apontam para ela e o aceite de termos é
    // imutável —, mas sem dado pessoal. Preenchido = conta excluída.
    @Column(name = "excluido_em")
    private Instant excluidoEm;

    // Limite de tentativas de senha (V23): erros seguidos desde o último acerto e, ao chegar no limite, até quando
    // a conta não aceita nova tentativa (login e exclusão de conta).
    @Column(name = "senha_falhas", nullable = false)
    private int senhaFalhas;

    @Column(name = "senha_bloqueada_ate")
    private Instant senhaBloqueadaAte;

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
        if (b.role != null) {
            this.papeis.add(b.role);
            if (b.role == UserRole.ROLE_PROVIDER) {
                this.papeis.add(UserRole.ROLE_CLIENT);   // todo prestador também contrata
            }
        }
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
    public int      getCpfHashVersao() { return cpfHashVersao; }

    public void setCpfHash(String hash) { this.cpfHash = hash; }

    /** Vincula o CPF (só o hash) e a versão da chave com que foi calculado. */
    public void vincularCpf(String hash, int versaoDaChave) {
        this.cpfHash       = hash;
        this.cpfHashVersao = versaoDaChave;
    }

    /** Papéis que a conta TEM (não o que está em uso na sessão). */
    public Set<UserRole> getPapeis() { return Collections.unmodifiableSet(papeis); }

    public boolean temPapel(UserRole papel) { return papeis.contains(papel); }

    /** O cliente que passa a prestar serviço (ou o contrário): a mesma conta ganha o papel. */
    public void concederPapel(UserRole papel) { papeis.add(papel); }

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

    public int     getSenhaFalhas()        { return senhaFalhas; }
    public Instant getSenhaBloqueadaAte()  { return senhaBloqueadaAte; }

    /** A conta não aceita tentativa de senha agora (limite de erros seguidos atingido, bloqueio ainda correndo). */
    public boolean senhaBloqueada(Instant agora) {
        return senhaBloqueadaAte != null && agora.isBefore(senhaBloqueadaAte);
    }

    /**
     * Conta um erro de senha; no {@code limite} bloqueia por {@code bloqueio}. Passado um bloqueio anterior, recomeça
     * do zero (senão a conta ficaria sempre a um erro de novo bloqueio). Durante o bloqueio não muda nada: quem
     * insiste não prolonga o bloqueio de quem é dono da conta.
     */
    public void registrarSenhaErrada(Instant agora, int limite, Duration bloqueio) {
        if (senhaBloqueada(agora)) {
            return;
        }
        if (senhaBloqueadaAte != null) {
            senhaFalhas = 0;
            senhaBloqueadaAte = null;
        }
        senhaFalhas++;
        if (senhaFalhas >= limite) {
            senhaBloqueadaAte = agora.plus(bloqueio);
        }
    }

    /** Senha certa, ou senha redefinida pelo e-mail: zera o contador e tira o bloqueio. */
    public void limparTentativasDeSenha() {
        senhaFalhas = 0;
        senhaBloqueadaAte = null;
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
