package com.onda.marketplace.servicerequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AiRateLimiterTest {

    private MutableClock clock;
    private AiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-16T12:00:00Z"));
        // 3 req/hora por usuário, 5 req/dia por usuário, 10 req/dia global
        limiter = new AiRateLimiter(clock, 3, 5, 10);
    }

    @Test
    void usuarioAbaixoDoLimite_permiteRequisicoes() {
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        assertThat(limiter.tryAcquire("user-1")).isTrue();
    }

    @Test
    void limiteHorarioPorUsuario_bloqueiaQuartaRequisicaoNaMesmaHora() {
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        // 4ª tentativa na mesma janela de 1 hora
        assertThat(limiter.tryAcquire("user-1")).isFalse();

        // Outro usuário ainda tem cota livre
        assertThat(limiter.tryAcquire("user-2")).isTrue();

        // Após avançar 61 minutos, nova cota horária é liberada
        clock.advance(Duration.ofMinutes(61));
        assertThat(limiter.tryAcquire("user-1")).isTrue();
    }

    @Test
    void limiteDiarioPorUsuario_bloqueiaAposTetoDiario() {
        // Consome 3 na hora 0
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("user-1")).isTrue();
        }
        // Avança 2 horas (reseta janela horária, mas mantém janela de 24h)
        clock.advance(Duration.ofHours(2));
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        assertThat(limiter.tryAcquire("user-1")).isTrue();
        // Atingiu 5 no dia -> próxima bloqueia mesmo com janela horária livre
        assertThat(limiter.tryAcquire("user-1")).isFalse();

        // Avança 25 horas -> janela de 24h limpa
        clock.advance(Duration.ofHours(25));
        assertThat(limiter.tryAcquire("user-1")).isTrue();
    }

    @Test
    void limiteGlobalDiario_bloqueiaTodosOsUsuariosAoAtingirTeto() {
        // 10 requisições divididas entre vários usuários (abaixo do teto individual)
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("user-" + i)).isTrue();
        }
        // 11ª requisição global é barrada para qualquer usuário
        assertThat(limiter.tryAcquire("user-novo")).isFalse();
        assertThat(limiter.tryAcquire("user-1")).isFalse();

        // Avança 25 horas
        clock.advance(Duration.ofHours(25));
        assertThat(limiter.tryAcquire("user-novo")).isTrue();
    }

    private static class MutableClock extends Clock {
        private Instant now;
        private final ZoneId zone = ZoneId.of("UTC");

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration d) {
            this.now = this.now.plus(d);
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

