package com.onda.marketplace.notification;

import com.onda.marketplace.admin.AdminReportService;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test para os endpoints M12 de notificações no AdminController.
 * Testa apenas os novos endpoints; os demais são cobertos em AdminControllerTest.
 */
@WebMvcTest(com.onda.marketplace.admin.AdminController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class NotificationControllerTest {

    @Autowired MockMvc mvc;

    // --- mocks obrigatórios para AdminController ---
    @MockBean com.onda.marketplace.admin.MediationService   mediationService;
    @MockBean com.onda.marketplace.admin.ModerationService  moderationService;
    @MockBean AdminReportService                            adminReportService;
    @MockBean com.onda.marketplace.admin.AdminQueryService  adminQueryService;
    @MockBean NotificationService                           notificationService;
    @MockBean com.onda.marketplace.admin.UserAdminService    userAdminService;
    @MockBean com.onda.marketplace.admin.ProviderAdminService providerAdminService;
    @MockBean com.onda.marketplace.audit.AuditService        auditService;

    // ----- GET /notifications -----

    @Test
    void listNotifications_semFiltro_retorna200ComLista() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.listar(null)).thenReturn(
                List.of(new AdminNotificationDto(id, "SOS", UUID.randomUUID(), Instant.now(), false)));

        mvc.perform(get("/api/v1/admin/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("SOS"))
                .andExpect(jsonPath("$[0].lida").value(false));
    }

    @Test
    void listNotifications_comFiltroNaoLida_passaParametroAoServico() throws Exception {
        when(notificationService.listar(false)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/notifications").param("lida", "false"))
                .andExpect(status().isOk());

        verify(notificationService).listar(false);
    }

    // ----- POST /notifications/{id}/read -----

    @Test
    void readNotification_retorna200EDelegaAoServico() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/admin/notifications/{id}/read", id).with(csrf()))
                .andExpect(status().isOk());

        verify(notificationService).marcarLida(id);
    }

    // ----- GET /reports/metrics.pdf -----

    @Test
    void reportMetricsPdf_retorna200ComContentTypeApplicationPdf() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake content".getBytes();
        when(adminReportService.exportarMetricasPdf()).thenReturn(fakePdf);

        mvc.perform(get("/api/v1/admin/reports/metrics.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("metrics.pdf")));
    }

    @Test
    void reportMetricsPdf_retornaByteArrayNaoVazio() throws Exception {
        when(adminReportService.exportarMetricasPdf()).thenReturn(new byte[]{1, 2, 3});

        mvc.perform(get("/api/v1/admin/reports/metrics.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
