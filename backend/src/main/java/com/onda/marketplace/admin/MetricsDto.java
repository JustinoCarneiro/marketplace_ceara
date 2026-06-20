package com.onda.marketplace.admin;

import java.math.BigDecimal;

/**
 * Métricas do dashboard administrativo (US23), derivadas por agregação sobre
 * service_requests, providers_profile e transactions — sem tabela própria de
 * verdade financeira (TS09).
 */
public record MetricsDto(
        long totalPedidos,
        long pedidosConcluidos,
        long pedidosEmDisputa,
        long prestadoresVerificados,
        long prestadoresEmVerificacao,
        BigDecimal receitaComissao
) {}
