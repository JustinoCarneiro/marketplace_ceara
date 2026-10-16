package com.onda.marketplace.servicerequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de taxa em memória para chamadas de IA (US14).
 *
 * <p>Protege contra consumo excessivo de cota e abusos em três níveis:
 * <ul>
 *   <li>Limite por usuário por hora (padrão: 5).</li>
 *   <li>Limite por usuário por dia (padrão: 20).</li>
 *   <li>Limite global por dia (padrão: 200).</li>
 * </ul>
 *
 * <p>Quando qualquer limite é atingido, {@link #tryAcquire(String)} retorna {@code false},
 * fazendo com que o serviço de IA caia graciosamente em {@code FALLBACK_MANUAL}
 * sem lançar erros e sem bloquear o cliente.
 */
@Component
public class AiRateLimiter {

    private final Clock clock;
    private final int maxUserHourly;
    private final int maxUserDaily;
    private final int maxGlobalDaily;

    private final Object lock = new Object();
    private final List<Instant> globalTimestamps = new ArrayList<>();
    private final Map<String, List<Instant>> userTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(
            @Value("${marketplace.ai.rate-limit.user-hourly:5}") int maxUserHourly,
            @Value("${marketplace.ai.rate-limit.user-daily:20}") int maxUserDaily,
            @Value("${marketplace.ai.rate-limit.global-daily:200}") int maxGlobalDaily) {
        this(Clock.systemUTC(), maxUserHourly, maxUserDaily, maxGlobalDaily);
    }

    public AiRateLimiter(Clock clock, int maxUserHourly, int maxUserDaily, int maxGlobalDaily) {
        this.clock = clock;
        this.maxUserHourly = maxUserHourly;
        this.maxUserDaily = maxUserDaily;
        this.maxGlobalDaily = maxGlobalDaily;
    }

    /**
     * Tenta adquirir uma permissão para chamada de IA.
     *
     * @param userKey identificador do usuário ou chave agregada
     * @return {@code true} se permitido; {@code false} se qualquer limite foi atingido
     */
    public boolean tryAcquire(String userKey) {
        String key = (userKey == null || userKey.isBlank()) ? "anonymous" : userKey.strip();
        Instant now = clock.instant();
        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        Instant oneDayAgo = now.minus(Duration.ofDays(1));

        synchronized (lock) {
            // 1. Limpeza e verificação global (últimas 24h)
            globalTimestamps.removeIf(t -> t.isBefore(oneDayAgo));
            if (globalTimestamps.size() >= maxGlobalDaily) {
                return false;
            }

            // 2. Limpeza e verificação por usuário
            List<Instant> userList = userTimestamps.computeIfAbsent(key, k -> new ArrayList<>());
            userList.removeIf(t -> t.isBefore(oneDayAgo));

            long hourlyCount = userList.stream().filter(t -> t.isAfter(oneHourAgo)).count();
            if (hourlyCount >= maxUserHourly) {
                return false;
            }
            if (userList.size() >= maxUserDaily) {
                return false;
            }

            // 3. Registra chamada permitida
            userList.add(now);
            globalTimestamps.add(now);

            // Limpa chaves vazias no mapa para não acumular memória
            if (userTimestamps.size() > 500) {
                userTimestamps.entrySet().removeIf(e -> e.getValue().isEmpty());
            }

            return true;
        }
    }
}

