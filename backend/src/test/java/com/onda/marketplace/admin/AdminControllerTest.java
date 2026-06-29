package com.onda.marketplace.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.audit.AuditService;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean MediationService    mediationService;
    @MockBean ModerationService   moderationService;
    @MockBean AdminReportService  adminReportService;
    @MockBean AdminQueryService   adminQueryService;
    @MockBean NotificationService notificationService;
    @MockBean UserAdminService     userAdminService;
    @MockBean ProviderAdminService providerAdminService;
    @MockBean AuditService         auditService;

    @Test
    void resolverDisputa_retorna200_eDelegaAoServico() throws Exception {
        UUID srId = UUID.randomUUID();
        var body = new ResolveDisputeRequest(MediationDecision.CONCLUIR, "serviço entregue");

        mvc.perform(post("/api/v1/admin/disputes/{id}/resolve", srId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(mediationService).resolver(eq(srId), any(), any(ResolveDisputeRequest.class));
    }

    @Test
    void moderarPrestador_retorna200_eDelegaAoServico() throws Exception {
        UUID userId = UUID.randomUUID();
        var body = new ModerateRequest(ModerationAction.SUSPENDER);

        mvc.perform(post("/api/v1/admin/providers/{userId}/moderate", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(moderationService).moderar(userId, ModerationAction.SUSPENDER);
    }

    @Test
    void metrics_retorna200_comAgregados() throws Exception {
        when(adminReportService.metrics()).thenReturn(
                new MetricsDto(42L, 30L, 2L, 15L, 5L, BigDecimal.valueOf(1234.50)));

        mvc.perform(get("/api/v1/admin/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidos").value(42))
                .andExpect(jsonPath("$.pedidosEmDisputa").value(2));
    }

    @Test
    void alerts_retorna200_comListaDeAlertas() throws Exception {
        when(adminReportService.alertas()).thenReturn(
                List.of(new OperationalAlert("SOS_ATIVO", 1L)));

        mvc.perform(get("/api/v1/admin/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("SOS_ATIVO"))
                .andExpect(jsonPath("$[0].quantidade").value(1));
    }

    @Test
    void reportCsv_retornaTextCsv() throws Exception {
        when(adminReportService.exportarCsv("transactions"))
                .thenReturn("id,serviceRequestId,valorTotal\n1,2,200");

        mvc.perform(get("/api/v1/admin/reports/{recurso}.csv", "transactions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("valorTotal")));
    }

    // --- M11: endpoints de query e reprocessamento ---

    @Test
    void disputes_retorna200_comLista() throws Exception {
        UUID srId = UUID.randomUUID();
        when(adminQueryService.findDisputas()).thenReturn(
                List.of(new DisputaAdminDto(srId, "ENCANADOR", BigDecimal.valueOf(300), Instant.now())));

        mvc.perform(get("/api/v1/admin/disputes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("ENCANADOR"))
                .andExpect(jsonPath("$[0].valorRetido").value(300));
    }

    @Test
    void disputeDetail_retorna200() throws Exception {
        UUID srId = UUID.randomUUID();
        when(adminQueryService.findDetalheDisputa(srId)).thenReturn(
                new DisputaDetalheDto(srId, "ELETRICISTA", "EM_DISPUTA",
                        BigDecimal.valueOf(500), TransactionStatus.RETIDO, null, Instant.now()));

        mvc.perform(get("/api/v1/admin/disputes/{id}", srId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("ELETRICISTA"));
    }

    @Test
    void transactions_retorna200_comListaFiltrada() throws Exception {
        UUID txId = UUID.randomUUID();
        when(adminQueryService.findTransacoes(TransactionStatus.RETIDO)).thenReturn(
                List.of(new TransacaoAdminDto(txId, UUID.randomUUID(),
                        BigDecimal.valueOf(200), TransactionStatus.RETIDO)));

        mvc.perform(get("/api/v1/admin/transactions").param("status", "RETIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statusPagamento").value("RETIDO"));
    }

    @Test
    void outbox_retorna200_comListaFiltrada() throws Exception {
        when(adminQueryService.findOutbox(OutboxStatus.FALHA)).thenReturn(
                List.of(new OutboxAdminDto(UUID.randomUUID(), "transaction",
                        "PAYMENT_RELEASED", 3, OutboxStatus.FALHA)));

        mvc.perform(get("/api/v1/admin/outbox").param("status", "FALHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipoEvento").value("PAYMENT_RELEASED"))
                .andExpect(jsonPath("$[0].tentativas").value(3));
    }

    @Test
    void reprocessarOutbox_retorna202() throws Exception {
        UUID outboxId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/outbox/{id}/reprocess", outboxId).with(csrf()))
                .andExpect(status().isAccepted());

        verify(adminQueryService).reprocessarOutbox(outboxId);
    }

    // --- novos endpoints: notificações, usuários e prestadores ---

    @Test
    void markAllNotificationsRead_retorna200_eDelega() throws Exception {
        mvc.perform(post("/api/v1/admin/notifications/mark-all-read").with(csrf()))
                .andExpect(status().isOk());

        verify(notificationService).marcarTodasLidas();
    }

    @Test
    void users_retorna200_comLista() throws Exception {
        when(userAdminService.listar(any())).thenReturn(List.of(
                new UserAdminDto(UUID.randomUUID(), "Maria", "maria@x.com", "ROLE_CLIENT", "ATIVO")));

        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Maria"))
                .andExpect(jsonPath("$[0].status").value("ATIVO"));
    }

    @Test
    void suspendUser_retorna200_eDelega() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/users/{id}/suspend", id).with(csrf()))
                .andExpect(status().isOk());

        verify(userAdminService).suspender(id);
        verify(auditService).registrar(any(), eq("SUSPENDER_USUARIO"), eq("user"), eq(id), any());
    }

    @Test
    void providers_retorna200_comLista() throws Exception {
        UUID userId = UUID.randomUUID();
        when(providerAdminService.listar(any())).thenReturn(List.of(
                new ProviderAdminDto(userId, "João", "eletrica", "VERIFICADO", null)));

        mvc.perform(get("/api/v1/admin/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("João"))
                .andExpect(jsonPath("$[0].statusVerificacao").value("VERIFICADO"));
    }

    @Test
    void verifyProvider_retorna200_eDelegaAprovar() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/providers/{userId}/verify", userId).with(csrf()))
                .andExpect(status().isOk());

        verify(moderationService).moderar(userId, ModerationAction.APROVAR);
    }

    @Test
    void rejectProvider_retorna200_eDelegaReprovar() throws Exception {
        UUID userId = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/providers/{userId}/reject", userId).with(csrf()))
                .andExpect(status().isOk());

        verify(moderationService).moderar(userId, ModerationAction.REPROVAR);
    }
}
