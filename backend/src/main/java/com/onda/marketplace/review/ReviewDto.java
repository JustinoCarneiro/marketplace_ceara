package com.onda.marketplace.review;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
        UUID   id,
        int    nota,
        String comentario,
        String tipo,
        Instant criadoEm
) {
    static ReviewDto from(Review r) {
        return new ReviewDto(r.getId(), r.getNota(), r.getComentario(),
                r.getTipo().name(), r.getCriadoEm());
    }
}
