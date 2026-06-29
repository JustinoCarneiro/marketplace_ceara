package com.onda.marketplace.category;

import java.util.UUID;

/**
 * DTO de saída do catálogo de categorias (US28). Records nunca expõem a
 * entidade diretamente (TS04).
 */
public record CategoryDto(UUID id, String nome, String slug, boolean ativa) {

    public static CategoryDto from(ServiceCategory c) {
        return new CategoryDto(c.getId(), c.getNome(), c.getSlug(), c.isAtiva());
    }
}
