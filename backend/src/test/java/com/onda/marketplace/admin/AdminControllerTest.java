package com.onda.marketplace.admin;

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
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean MediationService    mediationService;
    @MockBean ModerationService   moderationService;
    @MockBean AdminReportService  adminReportService;

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
}
