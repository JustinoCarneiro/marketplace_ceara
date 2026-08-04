package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Item da lista "Meus pedidos" do cliente (MyRequestsScreen). {@code propostas} é a
 * contagem de propostas ativas — a tela usa para o badge "N PROPOSTAS" em PROPOSTO.
 */
public record MyRequestDto(
        UUID    id,
        String  categoria,
        String  descricao,
        String  status,
        Instant updatedAt,
        String  prestadorNome,
        int     propostas,
        Transacao transacao
) {
    public record Transacao(BigDecimal valorTotal) {}
}
