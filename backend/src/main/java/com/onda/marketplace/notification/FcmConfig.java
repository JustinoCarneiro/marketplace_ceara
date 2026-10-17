package com.onda.marketplace.notification;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.List;

@Configuration(proxyBeanMethods=false)
class FcmConfig {
    @Bean
    PushGateway pushGateway(@Value("${notification.push.enabled:false}") boolean enabled,
            @Value("${notification.push.project-id:}") String project,
            com.fasterxml.jackson.databind.ObjectMapper json) throws java.io.IOException {
        if(!enabled) return new PushGateway() {
            public boolean enabled() { return false; }
            public Result send(String token,String kind,java.util.UUID ref,java.util.UUID id) { return Result.FAILED; }
        };
        if(!project.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]"))
            throw new IllegalStateException("Configure notification.push.project-id para ativar FCM.");
        GoogleCredentials credentials=GoogleCredentials.getApplicationDefault()
                .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(10000);
        return new FcmPushGateway(RestClient.builder().requestFactory(factory)
                .baseUrl("https://fcm.googleapis.com/v1/projects/"+project).build(), () -> {
                    try { credentials.refreshIfExpired(); return credentials.getAccessToken().getTokenValue(); }
                    catch(java.io.IOException ex) { throw new IllegalStateException("Autenticação FCM indisponível."); }
                },json);
    }
}
