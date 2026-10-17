package com.onda.marketplace.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/** API HTTP v1; payload genérico, autenticação OAuth2 de curta duração. */
final class FcmPushGateway implements PushGateway {
    private final RestClient client;
    private final Supplier<String> bearer;
    private final ObjectMapper json;
    FcmPushGateway(RestClient client,Supplier<String> bearer,ObjectMapper json) {
        this.client=client;this.bearer=bearer;this.json=json;
    }
    public boolean enabled() { return true; }
    public Result send(String token,String kind,UUID reference,UUID deliveryId) {
        Map<String,Object> payload=Map.of("message",Map.of("token",token,
            "notification",Map.of("title","Onda","body","Você tem uma atualização. Abra o aplicativo para consultar."),
            "data",Map.of("kind",kind,"referenceId",reference.toString(),"deliveryId",deliveryId.toString()),
            "android",Map.of("ttl","1800s","priority","HIGH","restricted_package_name","com.onda.marketplace",
                "notification",Map.of("channel_id","onda-updates"))));
        try {
            client.post().uri("/messages:send").headers(h->h.setBearerAuth(bearer.get()))
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
            return Result.SENT;
        } catch(RestClientResponseException ex) {
            if(unregistered(ex.getResponseBodyAsString())) return Result.UNREGISTERED;
            int status=ex.getStatusCode().value();
            if(status==429 || status>=500) throw new RetryLater(retryAfter(ex));
            return Result.FAILED;
        } catch(RuntimeException ex) { return Result.RETRY; }
    }
    private boolean unregistered(String body) {
        try {
            for(var detail:json.readTree(body).path("error").path("details"))
                if("type.googleapis.com/google.firebase.fcm.v1.FcmError".equals(detail.path("@type").asText())
                    && "UNREGISTERED".equals(detail.path("errorCode").asText())) return true;
        } catch(Exception ignored) { /* Nunca registrar resposta bruta. */ }
        return false;
    }
    private long retryAfter(RestClientResponseException ex) {
        String value=ex.getResponseHeaders()==null?null:ex.getResponseHeaders().getFirst("Retry-After");
        if(value==null) return 60;
        try { return Math.max(60,Long.parseLong(value)); }
        catch(NumberFormatException ignored) {
            try { return Math.max(60,Duration.between(Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); }
            catch(RuntimeException invalid) { return 60; }
        }
    }
    static final class RetryLater extends RuntimeException {
        final long seconds;
        RetryLater(long seconds) { super("Entrega push temporariamente indisponível.");this.seconds=seconds; }
    }
}
