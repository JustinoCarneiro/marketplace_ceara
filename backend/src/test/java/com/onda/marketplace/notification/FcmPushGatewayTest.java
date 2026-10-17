package com.onda.marketplace.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FcmPushGatewayTest {
    MockRestServiceServer server;
    FcmPushGateway gateway;
    UUID ref=UUID.randomUUID(), delivery=UUID.randomUUID();
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://fcm.googleapis.com/v1/projects/fixture-project");
        server=MockRestServiceServer.bindTo(builder).build();
        gateway=new FcmPushGateway(builder.build(),()->"fixture-oauth-token",new ObjectMapper());
    }
    @AfterEach void verify() { server.verify(); }
    @Test void sendsV1WithoutPersonalContent() {
        server.expect(requestTo("https://fcm.googleapis.com/v1/projects/fixture-project/messages:send"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer fixture-oauth-token"))
                .andExpect(jsonPath("$.message.token").value("fixture-device-token"))
                .andExpect(jsonPath("$.message.data.deliveryId").value(delivery.toString()))
                .andExpect(jsonPath("$.message.android.ttl").value("1800s"))
                .andExpect(jsonPath("$.message.notification.body").value("Você tem uma atualização. Abra o aplicativo para consultar."))
                .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        assertThat(gateway.send("fixture-device-token","NOVA_MENSAGEM",ref,delivery)).isEqualTo(PushGateway.Result.SENT);
    }
    @Test void onlyDocumentedUnregisteredErrorRemovesToken() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND).body("""
                {"error":{"details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"UNREGISTERED"}]}}
                """).contentType(MediaType.APPLICATION_JSON));
        assertThat(gateway.send("fixture-device-token","NOVA_MENSAGEM",ref,delivery)).isEqualTo(PushGateway.Result.UNREGISTERED);
    }
    @Test void invalidPayloadIsNotTreatedAsInvalidDevice() {
        server.expect(anything()).andRespond(withBadRequest().body("{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}"));
        assertThat(gateway.send("fixture-device-token","NOVA_MENSAGEM",ref,delivery)).isEqualTo(PushGateway.Result.FAILED);
    }
    @Test void honorsRetryAfterWithoutExposingResponseOrDeviceToken() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After","900").body("untrusted secret response fixture-device-token"));
        var error=catchThrowable(()->gateway.send("fixture-device-token","NOVA_MENSAGEM",ref,delivery));
        assertThat(error).isInstanceOf(FcmPushGateway.RetryLater.class).hasMessage("Entrega push temporariamente indisponível.");
        assertThat(((FcmPushGateway.RetryLater)error).seconds).isEqualTo(900);
        assertThat(error.getCause()).isNull();
    }
    @Test void failedAuthenticationDoesNotExposeCredentialException() {
        var client=RestClient.builder().build();
        var broken=new FcmPushGateway(client,()->{throw new IllegalStateException("private fixture credential");},new ObjectMapper());
        assertThat(broken.send("fixture-device-token","NOVA_MENSAGEM",ref,delivery)).isEqualTo(PushGateway.Result.RETRY);
    }
}
