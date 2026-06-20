package com.onda.marketplace.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de saída da central de notificações (M12/US30 — contrato do ROADMAP).
 * Records nunca expõem entidades diretamente (TS04).
 */
public record AdminNotificationDto(
        UUID    id,
        String  tipo,
        UUID    refId,
        Instant criadoEm,
        boolean lida
) {
    public static AdminNotificationDto from(AdminNotification n) {
        return new AdminNotificationDto(
                n.getId(), n.getTipo(), n.getRefId(), n.getCriadoEm(), n.isLida());
    }
}
