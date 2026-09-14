package com.onda.marketplace.payment;

import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = MercadoPagoWebhookController.class,
        properties = "marketplace.gateway.mercadopago.enabled=true")
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class MercadoPagoWebhookControllerTest {

    @Autowired MockMvc mvc;

    @MockBean MercadoPagoWebhookVerifier verifier;
    @MockBean MercadoPagoGatewayService  gateway;
    @MockBean PaymentService             paymentService;

    private static final String BODY = "{\"type\":\"payment\",\"data\":{\"id\":\"123456789\"}}";

    @Test
    void assinaturaValida_pagamentoAprovado_confirmaComoPAGO() throws Exception {
        when(verifier.valido(any(), any(), eq("123456789"))).thenReturn(true);
        when(gateway.consultarStatusPagamento("123456789")).thenReturn("approved");

        mvc.perform(post("/api/v1/payments/mercadopago/webhook").with(csrf())
                        .header("x-signature", "ts=1,v1=abc")
                        .header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(paymentService).confirmPayment("123456789", "PAGO");
    }

    @Test
    void pagamentoRejeitado_confirmaComoREJEITADO() throws Exception {
        when(verifier.valido(any(), any(), any())).thenReturn(true);
        when(gateway.consultarStatusPagamento("123456789")).thenReturn("rejected");

        mvc.perform(post("/api/v1/payments/mercadopago/webhook").with(csrf())
                        .header("x-signature", "ts=1,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(paymentService).confirmPayment("123456789", "REJEITADO");
    }

    @Test
    void assinaturaInvalida_retorna401_eNaoConfirmaNada() throws Exception {
        when(verifier.valido(any(), any(), any())).thenReturn(false);

        mvc.perform(post("/api/v1/payments/mercadopago/webhook").with(csrf())
                        .header("x-signature", "ts=1,v1=forjado")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).confirmPayment(any(), any());
        verify(gateway, never()).consultarStatusPagamento(any());
    }

    @Test
    void statusPendente_reconheceMasNaoConfirma() throws Exception {
        when(verifier.valido(any(), any(), any())).thenReturn(true);
        when(gateway.consultarStatusPagamento("123456789")).thenReturn("pending");

        mvc.perform(post("/api/v1/payments/mercadopago/webhook").with(csrf())
                        .header("x-signature", "ts=1,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(paymentService, never()).confirmPayment(any(), any());
    }

    @Test
    void tipoNaoPagamento_ignoraSemConsultarGateway() throws Exception {
        when(verifier.valido(any(), any(), any())).thenReturn(true);

        mvc.perform(post("/api/v1/payments/mercadopago/webhook").with(csrf())
                        .header("x-signature", "ts=1,v1=abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"merchant_order\",\"data\":{\"id\":\"999\"}}"))
                .andExpect(status().isOk());

        verify(gateway, never()).consultarStatusPagamento(any());
        verify(paymentService, never()).confirmPayment(any(), any());
    }
}
