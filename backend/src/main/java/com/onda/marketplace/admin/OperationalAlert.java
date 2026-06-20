package com.onda.marketplace.admin;

/**
 * Alerta operacional do painel (US30): SOS_ATIVO, DISPUTA_ABERTA,
 * VERIFICACAO_INCONCLUSIVA. Só é emitido quando a quantidade é positiva.
 */
public record OperationalAlert(String tipo, long quantidade) {}
