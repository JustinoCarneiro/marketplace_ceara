package com.onda.marketplace.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de saída da trilha de auditoria (US22) — alinhado ao {@code AuditPage}
 * do painel admin (id, adminNome, acao, entidade, criadoEm).
 */
public record AdminAuditLogDto(
        UUID    id,
        String  adminNome,
        String  acao,
        String  entidade,
        Instant criadoEm
) {
    public static AdminAuditLogDto from(AdminAuditLog a) {
        return new AdminAuditLogDto(
                a.getId(), a.getAdminNome(), a.getAcao(), a.getEntidade(), a.getCriadoEm());
    }
}
