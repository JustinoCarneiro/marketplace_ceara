package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Detalhe de um pedido para o app mobile (RequestDetailScreen) — inclui as partes
 * envolvidas e o estado do escrow. Records nunca expõem a entidade (TS04/LGPD):
 * do prestador vai só id e nome, nunca CPF.
 */
public record ServiceRequestDetailDto(
        UUID    id,
        String  status,
        String  categoria,
        String  descricao,
        UUID    clienteId,
        UUID    prestadorId,
        String  prestadorNome,
        Instant createdAt,
        Instant updatedAt,
        Transacao transacao
) {
    public record Transacao(String statusPagamento, BigDecimal valorTotal, BigDecimal valorComissao) {}
}
