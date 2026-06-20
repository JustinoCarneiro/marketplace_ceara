package com.onda.marketplace.admin;

import com.onda.marketplace.payment.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Detalhe de uma disputa para o mediador: dados do pedido, transação e
 * resolução (se já decidida). Campo {@code decisao} é null quando ainda
 * não resolvida.
 */
public record DisputaDetalheDto(
        UUID serviceRequestId,
        String categoria,
        String status,
        BigDecimal valorTotal,
        TransactionStatus statusPagamento,
        String decisao,
        Instant criadoEm
) {}
