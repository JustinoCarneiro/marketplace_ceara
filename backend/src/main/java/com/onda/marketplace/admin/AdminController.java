package com.onda.marketplace.admin;

import com.onda.marketplace.notification.AdminNotificationDto;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.TransactionStatus;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Painel administrativo (Épico 9) — superfície web, todas as rotas exigem
 * {@code ROLE_ADMIN} (403 caso contrário). Mediação de disputas, moderação de
 * prestadores, métricas, alertas operacionais e exportação de relatórios.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@SuppressWarnings("null")
public class AdminController {

    private final MediationService   mediationService;
    private final ModerationService  moderationService;
    private final AdminReportService adminReportService;
    private final AdminQueryService  adminQueryService;
    private final NotificationService notificationService;

    public AdminController(MediationService mediationService,
                           ModerationService moderationService,
                           AdminReportService adminReportService,
                           AdminQueryService adminQueryService,
                           NotificationService notificationService) {
        this.mediationService    = mediationService;
        this.moderationService   = moderationService;
        this.adminReportService  = adminReportService;
        this.adminQueryService   = adminQueryService;
        this.notificationService = notificationService;
    }

    @PostMapping("/disputes/{serviceRequestId}/resolve")
    public ResponseEntity<Void> resolverDisputa(
            @PathVariable UUID serviceRequestId,
            @Valid @RequestBody ResolveDisputeRequest request,
            Authentication auth) {

        UUID adminId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        mediationService.resolver(serviceRequestId, adminId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/providers/{userId}/moderate")
    public ResponseEntity<Void> moderarPrestador(
            @PathVariable UUID userId,
            @Valid @RequestBody ModerateRequest request) {

        moderationService.moderar(userId, request.action());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/metrics")
    public ResponseEntity<MetricsDto> metrics() {
        return ResponseEntity.ok(adminReportService.metrics());
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<OperationalAlert>> alerts() {
        return ResponseEntity.ok(adminReportService.alertas());
    }

    @GetMapping(value = "/reports/{recurso}.csv", produces = "text/csv")
    public ResponseEntity<String> reportCsv(@PathVariable String recurso) {
        String csv = adminReportService.exportarCsv(recurso);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"" + recurso + ".csv\"")
                .body(csv);
    }

    // --- M12: central de notificações e PDF de métricas ---

    /**
     * Lista alertas operacionais do painel (US30).
     * Filtro: {@code ?lida=true|false}; sem parâmetro retorna todos.
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<AdminNotificationDto>> listNotifications(
            @RequestParam(required = false) Boolean lida) {
        return ResponseEntity.ok(notificationService.listar(lida));
    }

    /** Marca uma notificação como lida (US30). */
    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> readNotification(@PathVariable UUID id) {
        notificationService.marcarLida(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Exporta relatório de métricas em PDF (US29).
     * NUNCA expõe CPF — somente agregados (TS04/LGPD).
     */
    @GetMapping(value = "/reports/metrics.pdf", produces = "application/pdf")
    public ResponseEntity<byte[]> reportMetricsPdf() {
        byte[] pdf = adminReportService.exportarMetricasPdf();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"metrics.pdf\"")
                .body(pdf);
    }

    // --- M11: fila de disputas, transações, outbox ---

    @GetMapping("/disputes")
    public ResponseEntity<List<DisputaAdminDto>> disputes() {
        return ResponseEntity.ok(adminQueryService.findDisputas());
    }

    @GetMapping("/disputes/{serviceRequestId}")
    public ResponseEntity<DisputaDetalheDto> disputeDetail(@PathVariable UUID serviceRequestId) {
        return ResponseEntity.ok(adminQueryService.findDetalheDisputa(serviceRequestId));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransacaoAdminDto>> transactions(
            @RequestParam(defaultValue = "RETIDO") TransactionStatus status) {
        return ResponseEntity.ok(adminQueryService.findTransacoes(status));
    }

    @GetMapping("/outbox")
    public ResponseEntity<List<OutboxAdminDto>> outbox(
            @RequestParam(defaultValue = "FALHA") OutboxStatus status) {
        return ResponseEntity.ok(adminQueryService.findOutbox(status));
    }

    @PostMapping("/outbox/{outboxId}/reprocess")
    public ResponseEntity<Void> reprocessarOutbox(@PathVariable UUID outboxId) {
        adminQueryService.reprocessarOutbox(outboxId);
        return ResponseEntity.accepted().build();
    }
}
