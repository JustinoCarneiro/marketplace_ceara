package com.onda.marketplace.auth;

import com.onda.marketplace.shared.exception.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A política de tentativas de senha (limite e duração do bloqueio vêm da configuração). */
class PasswordAttemptsTest {

    PasswordAttempts tentativas = new PasswordAttempts(3, 600);

    private static User usuario() {
        return User.builder().nome("Ana").email("ana@exemplo.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
    }

    @Test
    void contaNova_estaLiberada() {
        assertThatCode(() -> tentativas.exigirLiberada(usuario())).doesNotThrowAnyException();
    }

    @Test
    void abaixoDoLimite_continuaLiberada() {
        User u = usuario();

        tentativas.registrarErro(u);
        tentativas.registrarErro(u);

        assertThatCode(() -> tentativas.exigirLiberada(u)).doesNotThrowAnyException();
        assertThat(u.getSenhaFalhas()).isEqualTo(2);
    }

    @Test
    void noLimite_bloqueia_edizQuantoFalta() {
        User u = usuario();
        for (int i = 0; i < 3; i++) tentativas.registrarErro(u);

        assertThatThrownBy(() -> tentativas.exigirLiberada(u))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_ATTEMPTS")
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfterSeconds())
                        .isBetween(595L, 600L));   // o bloqueio configurado (600 s), menos o instante já gasto
    }

    @Test
    void acerto_liberaDeNovo() {
        User u = usuario();
        for (int i = 0; i < 3; i++) tentativas.registrarErro(u);

        tentativas.registrarAcerto(u);

        assertThatCode(() -> tentativas.exigirLiberada(u)).doesNotThrowAnyException();
        assertThat(u.getSenhaFalhas()).isZero();
    }

    @Test
    void limiteEBloqueioVemDaConfiguracao() {
        PasswordAttempts rigida = new PasswordAttempts(1, 120);
        User u = usuario();

        rigida.registrarErro(u);

        assertThatThrownBy(() -> rigida.exigirLiberada(u))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfterSeconds()).isBetween(115L, 120L));
    }

    @Test
    void reduzirAConfiguracaoDepoisNaoEncurtaOAvisoDeUmBloqueioJaGravado() {
        // Achado da revisão cruzada (2026-10-05): o tempo que falta vinha capado pela configuração ATUAL
        // (Math.min), não pelo que está gravado em senha_bloqueada_ate. Uma conta bloqueada sob lock-seconds=900
        // ainda tem ~900s pela frente mesmo que o operador reduza a configuração para 60s depois — a resposta
        // tem que dizer a verdade (o bloqueio gravado), não o valor novo da configuração.
        User u = usuario();
        u.registrarSenhaErrada(java.time.Instant.now(), 1, java.time.Duration.ofSeconds(900));   // bloqueio longo, já gravado

        PasswordAttempts configReduzidaDepois = new PasswordAttempts(1, 60);   // lock-seconds caiu de 900 para 60

        assertThatThrownBy(() -> configReduzidaDepois.exigirLiberada(u))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).getRetryAfterSeconds())
                        .as("tem de refletir os ~900s gravados, não os 60s da configuração nova")
                        .isGreaterThan(600L));
    }
}
