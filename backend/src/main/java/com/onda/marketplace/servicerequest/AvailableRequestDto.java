package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido aberto na fila do prestador (AvailableRequestsScreen). A faixa de orçamento
 * vem da sugestão da IA quando existe — é dica de precificação, não compromisso.
 * Nunca expõe dados do cliente (TS04/LGPD).
 */
public record AvailableRequestDto(
        UUID       id,
        String     titulo,
        String     descricao,
        String     categoria,
        String     status,
        BigDecimal orcamentoMin,
        BigDecimal orcamentoMax,
        Instant    criadoEm
) {
    static AvailableRequestDto from(ServiceRequest sr) {
        return new AvailableRequestDto(
                sr.getId(),
                sr.getDescricao(),
                sr.getDescricao(),
                sr.getCategoria(),
                sr.getStatus().name(),
                sr.getAiFaixaMin(),
                sr.getAiFaixaMax(),
                sr.getCreatedAt());
    }
}
