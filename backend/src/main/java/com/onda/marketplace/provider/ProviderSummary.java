package com.onda.marketplace.provider;

import java.math.BigDecimal;
import java.util.UUID;

/** Projeção explícita para listagem; nenhuma entidade User cruza esta consulta. */
public record ProviderSummary(UUID userId, String nome, String categoria, ProviderStatus statusVerificacao,
                              BigDecimal notaMedia, boolean cpfConciliado) {}
