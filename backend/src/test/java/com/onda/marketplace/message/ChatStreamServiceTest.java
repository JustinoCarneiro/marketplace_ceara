package com.onda.marketplace.message;

import org.junit.jupiter.api.*;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatStreamServiceTest {
    final ChatAccessService access = mock(ChatAccessService.class);
    final MessageRepository messages = mock(MessageRepository.class);
    final com.onda.marketplace.auth.UserRepository users = mock(com.onda.marketplace.auth.UserRepository.class);
    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    final UUID request = UUID.randomUUID(), user = UUID.randomUUID();
    ChatStreamService streams;

    @BeforeEach void setup() {
        streams = new ChatStreamService(access, messages, users, new TaskExecutorAdapter(executor),
                Clock.systemUTC(), timeout -> {
                    var emitter = mock(SseEmitter.class); emitters.add(emitter); return emitter;
                }, Duration.ofMillis(30), Duration.ofMinutes(2));
    }
    @AfterEach void close() { streams.close(); executor.shutdownNow(); }

    @Test void recycledEmitterDoesNotAbortCleanupOfOtherConnections() throws Exception {
        var first=streams.connect(request,user,Instant.now().plusSeconds(60));
        var second=streams.connect(request,user,Instant.now().plusSeconds(60));
        verify(first,timeout(2000).atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        verify(second,timeout(2000).atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("response already recycled")).when(first).complete();
        assertThatCode(streams::close).doesNotThrowAnyException();
        verify(second).complete();
    }

    @Test void unauthorizedUserCannotOpenStream() {
        doThrow(new com.onda.marketplace.shared.exception.BusinessException("FORBIDDEN", "Sem acesso"))
                .when(access).requireAccess(request, user);
        assertThatThrownBy(() -> streams.connect(request, user, Instant.now().plusSeconds(60)))
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        assertThat(emitters).isEmpty();
    }
    @Test void limitsSimultaneousConnectionsPerUser() {
        for (int i = 0; i < 3; i++) streams.connect(request, user, Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> streams.connect(request, user, Instant.now().plusSeconds(60)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(429));
    }
    @Test void revokedAccessClosesExistingConnection() throws Exception {
        SseEmitter emitter = streams.connect(request, user, Instant.now().plusSeconds(60));
        verify(emitter, timeout(2000)).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new com.onda.marketplace.shared.exception.BusinessException("FORBIDDEN", "Sem acesso"))
                .when(access).requireAccess(request, user);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(emitter).complete());
    }
    @Test void expiredTokenCannotOpenStream() {
        assertThatThrownBy(() -> streams.connect(request, user, Instant.now().minusSeconds(1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(emitters).isEmpty();
    }
    @Test void tokenExpiryClosesExistingConnection() throws Exception {
        var emitter = streams.connect(request, user, Instant.now().plusMillis(150));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(emitter).complete());
    }
    @Test void committedMessageIsSentOnlyToItsRequest() throws Exception {
        var one = streams.connect(request, user, Instant.now().plusSeconds(60));
        var otherRequest = UUID.randomUUID();
        var two = streams.connect(otherRequest, UUID.randomUUID(), Instant.now().plusSeconds(60));
        UUID id = UUID.randomUUID();
        var entity = mock(Message.class);
        when(entity.getId()).thenReturn(id);
        when(entity.getRemetenteId()).thenReturn(user);
        when(entity.getConteudo()).thenReturn("[contato removido]");
        when(entity.isMascarado()).thenReturn(true);
        when(entity.getCreatedAt()).thenReturn(Instant.now());
        when(messages.findById(id)).thenReturn(Optional.of(entity));
        when(users.findById(user)).thenReturn(Optional.empty());
        streams.onCommitted(new MessageCommitted(request, id));
        verify(one, timeout(2000).atLeastOnce()).send(argThat((SseEmitter.SseEventBuilder event) -> event.build().stream()
                .anyMatch(data -> data.getData() instanceof MessageDto dto && dto.id().equals(id) && dto.mascarado())));
        verify(two, never()).send(argThat((SseEmitter.SseEventBuilder event) -> event.build().stream()
                .anyMatch(data -> data.getData() instanceof MessageDto)));
    }
}
