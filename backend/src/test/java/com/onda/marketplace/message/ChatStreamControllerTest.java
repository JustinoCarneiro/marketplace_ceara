package com.onda.marketplace.message;

import com.onda.marketplace.auth.*;
import com.onda.marketplace.config.*;
import com.onda.marketplace.shared.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatStreamController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class ChatStreamControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ChatStreamService streams;
    @MockBean JwtService jwt;
    @MockBean UserRepository users;
    final UUID request = UUID.randomUUID(), user = UUID.randomUUID();
    String url() { return "/api/v1/service-requests/" + request + "/messages/stream"; }

    Instant token(String role) {
        var expires = Instant.now().plusSeconds(60);
        when(jwt.validateAndExtract("synthetic")).thenReturn(Jwts.claims().subject(user.toString())
                .add("role", role).expiration(Date.from(expires)).build());
        when(users.existsByIdAndAtivoTrue(user)).thenReturn(true);
        return expires;
    }
    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get(url()).accept("text/event-stream")).andExpect(status().isUnauthorized());
        verifyNoInteractions(streams);
    }
    @Test void requiresClientOrProviderRole() throws Exception {
        token("ROLE_ADMIN");
        mvc.perform(get(url()).header("Authorization", "Bearer synthetic").accept("text/event-stream"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(streams);
    }
    @Test void participantGetsStreamWithNoBufferingOrCaching() throws Exception {
        var expiry = token("ROLE_CLIENT");
        var emitter = new SseEmitter(60000L);
        emitter.send(SseEmitter.event().name("ready").data("{}"));
        when(streams.connect(eq(request), eq(user), any())).thenReturn(emitter);
        var result = mvc.perform(get(url()).header("Authorization", "Bearer synthetic").accept("text/event-stream"))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        verify(streams).connect(eq(request), eq(user), argThat(time -> time.getEpochSecond() == expiry.getEpochSecond()));
        emitter.complete();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }
    @Test void thirdPartyReceivesErrorBeforeStreamingStarts() throws Exception {
        token("ROLE_PROVIDER");
        when(streams.connect(eq(request), eq(user), any())).thenThrow(new BusinessException("FORBIDDEN", "Sem acesso"));
        mvc.perform(get(url()).header("Authorization", "Bearer synthetic").accept("text/event-stream"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
    @Test void browserPreflightAllowsHeadersUsedByNativeSseClient() throws Exception {
        mvc.perform(options(url()).header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization,cache-control,x-requested-with,last-event-id"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));
    }
}
