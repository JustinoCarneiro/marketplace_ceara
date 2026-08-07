package com.onda.marketplace.review;

import java.time.Instant;
import java.util.UUID;

/**
 * Avaliação devolvida a quem a escreveu. {@code revelada} distingue "já é pública"
 * de "aguardando a contraparte"; {@code prazoRevelacao} é quando ela vira pública
 * de qualquer jeito (ver double-blind em ReviewService).
 */
public record ReviewDto(
        UUID    id,
        int     nota,
        String  comentario,
        String  tipo,
        Instant criadoEm,
        boolean revelada,
        Instant prazoRevelacao
) {
    static ReviewDto from(Review r, Instant prazoRevelacao) {
        return new ReviewDto(r.getId(), r.getNota(), r.getComentario(),
                r.getTipo().name(), r.getCriadoEm(), r.isRevelada(), prazoRevelacao);
    }
}
