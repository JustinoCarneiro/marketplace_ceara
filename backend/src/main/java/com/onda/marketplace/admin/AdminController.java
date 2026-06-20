package com.onda.marketplace.admin;

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
public class AdminController {

    private final MediationService   mediationService;
    private final ModerationService  moderationService;
    private final AdminReportService adminReportService;

    public AdminController(MediationService mediationService,
                           ModerationService moderationService,
                           AdminReportService adminReportService) {
        this.mediationService   = mediationService;
        this.moderationService  = moderationService;
        this.adminReportService = adminReportService;
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
}
