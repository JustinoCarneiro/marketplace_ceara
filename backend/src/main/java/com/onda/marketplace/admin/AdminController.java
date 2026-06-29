package com.onda.marketplace.admin;

import com.onda.marketplace.audit.AuditService;
import com.onda.marketplace.notification.AdminNotificationDto;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.provider.ProviderStatus;
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

    private final MediationService    mediationService;
    private final ModerationService   moderationService;
    private final AdminReportService  adminReportService;
    private final AdminQueryService   adminQueryService;
    private final NotificationService notificationService;
    private final UserAdminService    userAdminService;
    private final ProviderAdminService providerAdminService;
    private final AuditService        auditService;

    public AdminController(MediationService mediationService,
                           ModerationService moderationService,
                           AdminReportService adminReportService,
                           AdminQueryService adminQueryService,
                           NotificationService notificationService,
                           UserAdminService userAdminService,
                           ProviderAdminService providerAdminService,
                           AuditService auditService) {
        this.mediationService     = mediationService;
        this.moderationService    = moderationService;
        this.adminReportService   = adminReportService;
        this.adminQueryService    = adminQueryService;
        this.notificationService  = notificationService;
        this.userAdminService     = userAdminService;
        this.providerAdminService = providerAdminService;
        this.auditService         = auditService;
    }

    /** Extrai o id do admin autenticado (subject do JWT). */
    private static UUID adminId(Authentication auth) {
        return auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
    }

    @PostMapping("/disputes/{serviceRequestId}/resolve")
    public ResponseEntity<Void> resolverDisputa(
            @PathVariable UUID serviceRequestId,
            @Valid @RequestBody ResolveDisputeRequest request,
            Authentication auth) {

        UUID adminId = adminId(auth);
        mediationService.resolver(serviceRequestId, adminId, request);
        auditService.registrar(adminId, "RESOLVER_DISPUTA", "service_request", serviceRequestId, null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/providers/{userId}/moderate")
    public ResponseEntity<Void> moderarPrestador(
            @PathVariable UUID userId,
            @Valid @RequestBody ModerateRequest request,
            Authentication auth) {

        moderationService.moderar(userId, request.action());
        auditService.registrar(adminId(auth), "MODERAR_PRESTADOR", "provider", userId,
                request.action().name());
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
    public ResponseEntity<Void> reprocessarOutbox(@PathVariable UUID outboxId, Authentication auth) {
        adminQueryService.reprocessarOutbox(outboxId);
        auditService.registrar(adminId(auth), "REPROCESSAR_OUTBOX", "outbox_event", outboxId, null);
        return ResponseEntity.accepted().build();
    }

    /** Marca todas as notificações não lidas como lidas (US30). */
    @PostMapping("/notifications/mark-all-read")
    public ResponseEntity<Void> markAllNotificationsRead() {
        notificationService.marcarTodasLidas();
        return ResponseEntity.ok().build();
    }

    // --- M10: gestão de usuários (US26) ---

    /** Lista usuários, com busca opcional por nome/e-mail (US26). */
    @GetMapping("/users")
    public ResponseEntity<List<UserAdminDto>> users(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(userAdminService.listar(q));
    }

    @PostMapping("/users/{id}/suspend")
    public ResponseEntity<Void> suspendUser(@PathVariable UUID id, Authentication auth) {
        userAdminService.suspender(id);
        auditService.registrar(adminId(auth), "SUSPENDER_USUARIO", "user", id, null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{id}/reactivate")
    public ResponseEntity<Void> reactivateUser(@PathVariable UUID id, Authentication auth) {
        userAdminService.reativar(id);
        auditService.registrar(adminId(auth), "REATIVAR_USUARIO", "user", id, null);
        return ResponseEntity.ok().build();
    }

    // --- M10: lista e moderação de prestadores (US25) ---

    /** Lista prestadores, com filtro opcional por {@code ?statusVerificacao=} (US25). */
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderAdminDto>> providers(
            @RequestParam(required = false) ProviderStatus statusVerificacao) {
        return ResponseEntity.ok(providerAdminService.listar(statusVerificacao));
    }

    /** Aprova a verificação do prestador (US25) — atalho para {@code moderate=APROVAR}. */
    @PostMapping("/providers/{userId}/verify")
    public ResponseEntity<Void> verifyProvider(@PathVariable UUID userId, Authentication auth) {
        moderationService.moderar(userId, ModerationAction.APROVAR);
        auditService.registrar(adminId(auth), "VERIFICAR_PRESTADOR", "provider", userId, null);
        return ResponseEntity.ok().build();
    }

    /** Reprova a verificação do prestador (US25) — atalho para {@code moderate=REPROVAR}. */
    @PostMapping("/providers/{userId}/reject")
    public ResponseEntity<Void> rejectProvider(@PathVariable UUID userId, Authentication auth) {
        moderationService.moderar(userId, ModerationAction.REPROVAR);
        auditService.registrar(adminId(auth), "REPROVAR_PRESTADOR", "provider", userId, null);
        return ResponseEntity.ok().build();
    }
}
