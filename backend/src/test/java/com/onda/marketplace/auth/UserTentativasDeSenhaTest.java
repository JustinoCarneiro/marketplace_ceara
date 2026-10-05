package com.onda.marketplace.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Limite de tentativas de senha: o contador e o bloqueio vivem na conta, e o instante entra como parâmetro. */
class UserTentativasDeSenhaTest {

    private static final Instant T0 = Instant.parse("2026-10-05T12:00:00Z");
    private static final Duration BLOQUEIO = Duration.ofMinutes(15);
    private static final int LIMITE = 5;

    private static User usuario() {
        return User.builder().nome("Ana").email("ana@exemplo.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
    }

    private static void erros(User u, int quantos, Instant quando) {
        for (int i = 0; i < quantos; i++) u.registrarSenhaErrada(quando, LIMITE, BLOQUEIO);
    }

    @Test
    void contaNova_naoTemFalhasNemBloqueio() {
        User u = usuario();

        assertThat(u.getSenhaFalhas()).isZero();
        assertThat(u.senhaBloqueada(T0)).isFalse();
    }

    @Test
    void abaixoDoLimite_contaOsErros_masNaoBloqueia() {
        User u = usuario();

        erros(u, LIMITE - 1, T0);

        assertThat(u.getSenhaFalhas()).isEqualTo(LIMITE - 1);
        assertThat(u.senhaBloqueada(T0)).isFalse();
    }

    @Test
    void noLimite_bloqueiaPeloTempoDefinido_edepoisLibera() {
        User u = usuario();

        erros(u, LIMITE, T0);

        assertThat(u.senhaBloqueada(T0)).isTrue();
        assertThat(u.senhaBloqueada(T0.plus(BLOQUEIO).minusSeconds(1))).isTrue();
        assertThat(u.senhaBloqueada(T0.plus(BLOQUEIO))).isFalse();
        assertThat(u.getSenhaBloqueadaAte()).isEqualTo(T0.plus(BLOQUEIO));
    }

    @Test
    void passadoOBloqueio_oProximoErroRecomecaDoZero() {
        User u = usuario();
        erros(u, LIMITE, T0);

        u.registrarSenhaErrada(T0.plus(BLOQUEIO).plusSeconds(1), LIMITE, BLOQUEIO);

        // senão a conta ficaria a um erro de um novo bloqueio para sempre
        assertThat(u.getSenhaFalhas()).isEqualTo(1);
        assertThat(u.senhaBloqueada(T0.plus(BLOQUEIO).plusSeconds(1))).isFalse();
    }

    @Test
    void erroDuranteOBloqueio_naoEstendeNemMudaNada() {
        User u = usuario();
        erros(u, LIMITE, T0);
        Instant ate = u.getSenhaBloqueadaAte();

        u.registrarSenhaErrada(T0.plusSeconds(60), LIMITE, BLOQUEIO);

        // quem insiste não prolonga o bloqueio de quem é dono da conta
        assertThat(u.getSenhaBloqueadaAte()).isEqualTo(ate);
        assertThat(u.getSenhaFalhas()).isEqualTo(LIMITE);
    }

    @Test
    void senhaCerta_zeraOContador_eLimpaOBloqueio() {
        User u = usuario();
        erros(u, LIMITE, T0);

        u.limparTentativasDeSenha();

        assertThat(u.getSenhaFalhas()).isZero();
        assertThat(u.getSenhaBloqueadaAte()).isNull();
        assertThat(u.senhaBloqueada(T0)).isFalse();
    }
}
