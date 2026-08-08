package com.onda.marketplace.denuncia;

import java.time.Instant;
import java.util.UUID;

public record DenunciaDto(UUID id, String tipo, String status, Instant criadoEm) {
    public static DenunciaDto from(Denuncia d) {
        return new DenunciaDto(d.getId(), d.getTipo().name(), d.getStatus().name(), d.getCriadoEm());
    }
}
