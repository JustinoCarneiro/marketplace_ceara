package com.onda.marketplace.admin;

import com.onda.marketplace.payment.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detalhe de uma disputa para o mediador: dados do pedido, das partes envolvidas,
 * mídias anexadas, transação e resolução (se já decidida). Campo {@code decisao}
 * é null quando ainda não resolvida. US24: sem cliente/prestador/mídias o mediador
 * decidia às cegas quem recebe o valor retido.
 */
public record DisputaDetalheDto(
        UUID serviceRequestId,
        String categoria,
        String status,
        BigDecimal valorTotal,
        TransactionStatus statusPagamento,
        String motivoDisputa,
        String detalhesDisputa,
        String decisao,
        Instant criadoEm,
        String clienteNome,
        String prestadorNome,
        List<MidiaDto> midias
) {
    public record MidiaDto(String tipo, String url) {}
}
