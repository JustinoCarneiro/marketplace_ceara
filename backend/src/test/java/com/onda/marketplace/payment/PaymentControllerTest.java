package com.onda.marketplace.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class PaymentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  PaymentService paymentService;

    private static final UUID   SR_ID          = UUID.randomUUID();
    private static final UUID   IDEM_ID        = UUID.randomUUID();
    private static final String WEBHOOK_SECRET = "test-webhook-secret-for-tests-only";

    @Test
    void initiatePayment_validRequest_returns201() throws Exception {
        var dto = new TransactionDto(UUID.randomUUID(), SR_ID, BigDecimal.valueOf(250),
                "PIX", "PENDENTE", Instant.now());
        when(paymentService.initiate(any(), any(), any(), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/service-requests/{id}/payment", SR_ID)
                        .with(csrf())
                        .header("X-Idempotency-Key", IDEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new InitiatePaymentRequest("PIX"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusPagamento").value("PENDENTE"))
                .andExpect(jsonPath("$.metodo").value("PIX"));
    }

    @Test
    void initiatePayment_missingIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/payment", SR_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new InitiatePaymentRequest("PIX"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhook_comSecretoValido_returns200() throws Exception {
        var payload = new WebhookPayload(UUID.randomUUID().toString(), "PAGO");
        mvc.perform(post("/api/v1/payments/webhook")
                        .with(csrf())
                        .header("X-Webhook-Secret", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    @Test
    void webhook_semSecretoOuSecreroErrado_returns401() throws Exception {
        var payload = new WebhookPayload(UUID.randomUUID().toString(), "PAGO");

        mvc.perform(post("/api/v1/payments/webhook")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/payments/webhook")
                        .with(csrf())
                        .header("X-Webhook-Secret", "segredo-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized());
    }
}
