package com.onda.marketplace.message;

import org.springframework.web.server.ResponseStatusException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import com.onda.marketplace.auth.JwtService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests/{id}/messages/stream")
public class ChatStreamController {
    private final ChatStreamService streams;
    private final JwtService jwt;
    ChatStreamController(ChatStreamService streams, JwtService jwt) { this.streams = streams; this.jwt = jwt; }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public ResponseEntity<SseEmitter> subscribe(@PathVariable UUID id, Authentication auth,
                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        Claims claims;
        try { claims = jwt.validateAndExtract(authorization.substring(7)); }
        catch (JwtException expired) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        var emitter = streams.connect(id, UUID.fromString(auth.getName()), claims.getExpiration().toInstant());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no").body(emitter);
    }
}
