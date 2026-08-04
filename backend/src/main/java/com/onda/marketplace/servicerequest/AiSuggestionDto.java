package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;

/**
 * Sugestão da IA já persistida no pedido (AiAssistantScreen). Campos nulos quando a
 * IA estava indisponível na criação — a tela cai no preenchimento manual (fallback
 * obrigatório do CLAUDE.md), nunca bloqueia.
 */
public record AiSuggestionDto(
        String     descricaoSugerida,
        BigDecimal faixaMin,
        BigDecimal faixaMax
) {
    static AiSuggestionDto from(ServiceRequest sr) {
        return new AiSuggestionDto(sr.getAiDescricaoSugerida(), sr.getAiFaixaMin(), sr.getAiFaixaMax());
    }
}
