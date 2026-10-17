package com.onda.marketplace.message;

import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;

/** SSE local à instância. Cada conexão tem fila limitada e um escritor em virtual thread. */
@Service
class ChatStreamService {
    private final ChatAccessService access;
    private final MessageRepository messages;
    private final UserRepository users;
    private final AsyncTaskExecutor executor;
    private final Clock clock;
    private final LongFunction<SseEmitter> emitterFactory;
    private final Duration heartbeat;
    private final Duration lifetime;
    private final Map<UUID, Subscription> connections = new ConcurrentHashMap<>();

    @Autowired
    ChatStreamService(ChatAccessService access, MessageRepository messages, UserRepository users,
                      @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this(access, messages, users, executor, Clock.systemUTC(), SseEmitter::new,
                Duration.ofSeconds(15), Duration.ofMinutes(2));
    }

    ChatStreamService(ChatAccessService access, MessageRepository messages, UserRepository users,
                      AsyncTaskExecutor executor, Clock clock, LongFunction<SseEmitter> emitterFactory,
                      Duration heartbeat, Duration lifetime) {
        this.access = access;
        this.messages = messages;
        this.users = users;
        this.executor = executor;
        this.clock = clock;
        this.emitterFactory = emitterFactory;
        this.heartbeat = heartbeat;
        this.lifetime = lifetime;
    }

    SseEmitter connect(UUID requestId, UUID userId, Instant tokenExpiry) {
        if (!tokenExpiry.isAfter(clock.instant())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        access.requireAccess(requestId, userId);
        Instant deadline = Collections.min(List.of(tokenExpiry, clock.instant().plus(lifetime)));
        long timeout = Math.max(1, Duration.between(clock.instant(), deadline).toMillis());
        Subscription subscription;
        synchronized (connections) {
            if (connections.size() >= 256 || connections.values().stream().filter(c -> c.userId.equals(userId)).count() >= 3) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de conexões do chat.");
            }
            subscription = new Subscription(requestId, userId, tokenExpiry, deadline, emitterFactory.apply(timeout + 5000));
            connections.put(subscription.id, subscription);
        }
        var emitter = subscription.emitter;
        emitter.onCompletion(() -> subscription.stop(false));
        emitter.onTimeout(() -> subscription.stop(false));
        emitter.onError(error -> subscription.stop(false));
        try {
            subscription.writer = executor.submit(() -> write(subscription));
        } catch (RuntimeException failure) {
            subscription.stop(true);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat temporariamente indisponível.");
        }
        return emitter;
    }

    /** Somente após commit; a consulta/entrega ocorre fora dos recursos da transação original. */
    @TransactionalEventListener
    public void onCommitted(MessageCommitted event) {
        try { executor.execute(() -> {
            if (connections.values().stream().noneMatch(c -> c.requestId.equals(event.requestId()))) return;
            messages.findById(event.messageId()).ifPresent(message -> {
                String name = users.findById(message.getRemetenteId()).map(User::getNome).orElse("Usuário");
                var dto = new MessageDto(message.getId(), message.getRemetenteId(), name,
                        message.getConteudo(), message.isMascarado(), message.getCreatedAt());
                for (Subscription connection : connections.values()) {
                    if (connection.requestId.equals(event.requestId()) && !connection.pending.offer(dto)) connection.stop(true);
                }
            });
        }); } catch (TaskRejectedException unavailable) {
            // Persistência já confirmou: falha de push não pode transformar o POST confirmado em erro.
            LoggerFactory.getLogger(ChatStreamService.class)
                    .warn("Entrega ao vivo indisponível; histórico será recuperado na reconexão.");
        }
    }

    private void write(Subscription connection) {
        connection.writerThread = Thread.currentThread();
        try {
            access.requireAccess(connection.requestId, connection.userId);
            connection.emitter.send(SseEmitter.event().name("ready").data("{}"));
            while (!connection.closed.get()) {
                long remaining = Duration.between(clock.instant(), connection.deadline).toMillis();
                if (remaining <= 0) {
                    if (!connection.tokenExpiry.isAfter(clock.instant())) {
                        connection.emitter.send(SseEmitter.event().name("session-expired").data("{}"));
                    } else connection.emitter.send(SseEmitter.event().name("reconnect").data("{}"));
                    connection.stop(true);
                    return;
                }
                var message = connection.pending.poll(Math.min(heartbeat.toMillis(), remaining), TimeUnit.MILLISECONDS);
                if (!connection.tokenExpiry.isAfter(clock.instant())) {
                    connection.emitter.send(SseEmitter.event().name("session-expired").data("{}"));
                    connection.stop(true);
                    return;
                }
                access.requireAccess(connection.requestId, connection.userId);
                if (message == null) connection.emitter.send(SseEmitter.event().name("heartbeat").data("{}"));
                else connection.emitter.send(SseEmitter.event().id(message.id().toString()).name("message").data(message));
            }
        } catch (BusinessException revoked) {
            try { connection.emitter.send(SseEmitter.event().name("access-revoked").data("{}")); }
            catch (IOException | IllegalStateException ignored) { /* conexão já encerrada */ }
            connection.stop(true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            connection.stop(true);
        } catch (IOException | RuntimeException failure) {
            connection.stop(true);
        }
    }

    @PreDestroy
    public void close() { connections.values().forEach(c -> c.stop(true)); }

    private final class Subscription {
        final UUID id = UUID.randomUUID();
        final UUID requestId;
        final UUID userId;
        final Instant tokenExpiry;
        final Instant deadline;
        final SseEmitter emitter;
        final BlockingQueue<MessageDto> pending = new ArrayBlockingQueue<>(64);
        final AtomicBoolean closed = new AtomicBoolean();
        volatile Future<?> writer;
        volatile Thread writerThread;
        Subscription(UUID requestId, UUID userId, Instant tokenExpiry, Instant deadline, SseEmitter emitter) {
            this.requestId = requestId;
            this.userId = userId;
            this.tokenExpiry = tokenExpiry;
            this.deadline = deadline;
            this.emitter = emitter;
        }
        void stop(boolean complete) {
            if (!closed.compareAndSet(false, true)) return;
            connections.remove(id);
            pending.clear();
            if (writer != null && Thread.currentThread() != writerThread) writer.cancel(true);
            if (complete) {
                try { emitter.complete(); }
                catch (IllegalStateException recycled) { /* O servlet já encerrou/reciclou a resposta. */ }
            }
        }
    }
}
