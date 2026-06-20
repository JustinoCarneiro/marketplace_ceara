package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestDto(
        UUID       id,
        String     categoria,
        String     descricao,
        String     status,
        String     aiDescricaoSugerida,
        BigDecimal aiFaixaMin,
        BigDecimal aiFaixaMax,
        Instant    createdAt
) {
    static ServiceRequestDto from(ServiceRequest sr) {
        return new ServiceRequestDto(
                sr.getId(),
                sr.getCategoria(),
                sr.getDescricao(),
                sr.getStatus().name(),
                sr.getAiDescricaoSugerida(),
                sr.getAiFaixaMin(),
                sr.getAiFaixaMax(),
                sr.getCreatedAt());
    }
}
