package com.onda.marketplace.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de saída da trilha de auditoria (US22) — alinhado ao {@code AuditPage}
 * do painel admin (id, adminNome, acao, entidade, criadoEm, detalhe).
 *
 * <p>{@code detalhe} já era gravado pelo {@link AuditService} para várias ações
 * (ex.: justificativa de moderação de prestador) mas nunca saía por aqui — a
 * trilha existia no banco e ficava invisível para quem mais precisava dela.
 */
public record AdminAuditLogDto(
        UUID    id,
        String  adminNome,
        String  acao,
        String  entidade,
        Instant criadoEm,
        String  detalhe
) {
    public static AdminAuditLogDto from(AdminAuditLog a) {
        return new AdminAuditLogDto(
                a.getId(), a.getAdminNome(), a.getAcao(), a.getEntidade(), a.getCriadoEm(), a.getDetalhe());
    }
}
