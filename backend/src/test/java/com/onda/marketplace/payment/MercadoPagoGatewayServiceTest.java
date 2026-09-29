package com.onda.marketplace.payment;

import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SuppressWarnings("null")
class MercadoPagoGatewayServiceTest {

    private static final UUID SR_ID = UUID.randomUUID();

    private MockRestServiceServer server;
    private MercadoPagoGatewayService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        ServiceRequestRepository srRepo = mock(ServiceRequestRepository.class);
        when(srRepo.findClienteEmailBySrId(SR_ID)).thenReturn(Optional.of("cliente@example.com"));

        service = new MercadoPagoGatewayService(
                builder, srRepo,
                "https://api.mercadopago.com", "TEST-TOKEN",
                "https://onda.test/api/v1/payments/mercadopago/webhook", 30);
    }

    private Transaction pixTx() {
        return new Transaction(SR_ID, new BigDecimal("250.00"), new BigDecimal("37.50"),
                new BigDecimal("0.15"), PaymentMethod.PIX, "idem-key-123");
    }

    @Test
    void cobrar_criaPagamentoPixNoMercadoPago_eDevolveOId() {
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-TOKEN"))
                .andExpect(header("X-Idempotency-Key", "idem-key-123"))
                .andExpect(jsonPath("$.payment_method_id").value("pix"))
                .andExpect(jsonPath("$.transaction_amount").exists())
                .andExpect(jsonPath("$.payer.email").value("cliente@example.com"))
                .andExpect(jsonPath("$.external_reference").exists())
                .andExpect(jsonPath("$.notification_url").value(
                        "https://onda.test/api/v1/payments/mercadopago/webhook"))
                .andRespond(withSuccess(
                        "{\"id\": 123456789, \"status\": \"pending\"}", MediaType.APPLICATION_JSON));

        String gatewayId = service.cobrar(pixTx());

        assertThat(gatewayId).isEqualTo("123456789");
        server.verify();
    }

    @Test
    void cobrar_guardaQrCodeECopiaECola_paraExibicaoNaTela() {
        // Formato real confirmado ao vivo em 2026-09-28 (cobrança de R$1 real, ver
        // memoria-tecnica/bugs/mercadopago-payer-email-forbidden-sandbox.md) — não é
        // suposição de contrato.
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andRespond(withSuccess("""
                        {
                          "id": 180383866387,
                          "status": "pending",
                          "point_of_interaction": {
                            "transaction_data": {
                              "qr_code": "00020126500014br.gov.bcb.pix...6304068B",
                              "qr_code_base64": "iVBORw0KGgoAAAANSU...",
                              "ticket_url": "https://www.mercadopago.com.br/payments/180383866387/ticket"
                            }
                          }
                        }""", MediaType.APPLICATION_JSON));

        Transaction tx = pixTx();
        service.cobrar(tx);

        assertThat(tx.getPixQrCode()).isEqualTo("00020126500014br.gov.bcb.pix...6304068B");
        assertThat(tx.getPixQrCodeBase64()).isEqualTo("iVBORw0KGgoAAAANSU...");
        assertThat(tx.getPixTicketUrl())
                .isEqualTo("https://www.mercadopago.com.br/payments/180383866387/ticket");
    }

    @Test
    void cobrar_semDadosDePix_naoQuebraENaoPreencheOsCampos() {
        // Resposta mínima (o teste original da suíte, antes de existir QR) continua válida —
        // point_of_interaction ausente não pode virar NullPointerException.
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andRespond(withSuccess("{\"id\": 1, \"status\": \"pending\"}", MediaType.APPLICATION_JSON));

        Transaction tx = pixTx();
        service.cobrar(tx);

        assertThat(tx.getPixQrCode()).isNull();
        assertThat(tx.getPixQrCodeBase64()).isNull();
        assertThat(tx.getPixTicketUrl()).isNull();
    }

    @Test
    void cobrar_quandoMercadoPagoRecusa_propagaOErro() {
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andRespond(withBadRequest().body("{\"message\":\"invalid parameter\"}"));

        assertThatThrownBy(() -> service.cobrar(pixTx()))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    @Test
    void cobrar_semEmailDoCliente_falhaAntesDeChamarOGateway() {
        ServiceRequestRepository semEmail = mock(ServiceRequestRepository.class);
        when(semEmail.findClienteEmailBySrId(SR_ID)).thenReturn(Optional.empty());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer isolado = MockRestServiceServer.bindTo(builder).build();
        var svc = new MercadoPagoGatewayService(
                builder, semEmail, "https://api.mercadopago.com", "T", "", 30);

        assertThatThrownBy(() -> svc.cobrar(pixTx()))
                .isInstanceOf(IllegalStateException.class);
        isolado.verify(); // nenhuma chamada HTTP esperada nem feita
    }

    @Test
    void liberar_semPayoutDisponivel_lancaManualPayoutRequired() {
        assertThatThrownBy(() -> service.liberar(pixTx()))
                .isInstanceOf(ManualPayoutRequiredException.class);
    }

    @Test
    void reembolsar_solicitaRefundTotalNoPagamentoOriginal() {
        Transaction tx = pixTx();
        tx.setGatewayTransactionId("123456789");

        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123456789/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-TOKEN"))
                .andExpect(header("X-Idempotency-Key", "idem-key-123:refund"))
                .andRespond(withSuccess("{\"id\":987,\"status\":\"approved\"}", MediaType.APPLICATION_JSON));

        service.reembolsar(tx);
        server.verify();
    }

    @Test
    void reembolsar_semGatewayTransactionId_falhaAntesDoHttp() {
        assertThatThrownBy(() -> service.reembolsar(pixTx()))
                .isInstanceOf(IllegalStateException.class);
        server.verify(); // nenhuma chamada esperada
    }

    @Test
    void reembolsar_quandoMercadoPagoRecusa_propagaOErro() {
        Transaction tx = pixTx();
        tx.setGatewayTransactionId("123456789");
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123456789/refunds"))
                .andRespond(withBadRequest().body("{\"message\":\"cannot refund\"}"));

        assertThatThrownBy(() -> service.reembolsar(tx))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }
}
