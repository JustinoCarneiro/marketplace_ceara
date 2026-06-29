package com.onda.marketplace.audit;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leitura da trilha de auditoria (US22) — superfície admin, exige {@code ROLE_ADMIN}.
 * Somente leitura: a escrita é feita pelo {@link AuditService} nos pontos de mutação.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditAdminController {

    private final AuditService auditService;

    public AuditAdminController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<AdminAuditLogDto>> listar() {
        return ResponseEntity.ok(auditService.listar());
    }
}
