package com.onda.marketplace.sos;

import java.time.Instant;
import java.util.UUID;

public record SosAlertDto(
        UUID    id,
        UUID    userId,
        UUID    serviceRequestId,
        String  status,
        Instant criadoEm
) {
    static SosAlertDto from(SosAlert a) {
        return new SosAlertDto(a.getId(), a.getUserId(), a.getServiceRequestId(),
                a.getStatus().name(), a.getCriadoEm());
    }
}
