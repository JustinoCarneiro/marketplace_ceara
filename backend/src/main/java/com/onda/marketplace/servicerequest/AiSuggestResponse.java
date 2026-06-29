package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;

/**
 * Response do endpoint de sugestão por IA (US14 / M04).
 *
 * <p>{@code source} é sempre informado:
 * <ul>
 *   <li>{@code "AI"} — resposta gerada pelo modelo de IA.</li>
 *   <li>{@code "FALLBACK_MANUAL"} — IA indisponível; o cliente segue manualmente
 *       (princípio do CLAUDE.md: IA nunca é caminho crítico bloqueante).</li>
 * </ul>
 */
public record AiSuggestResponse(
        String     descricaoSugerida,
        BigDecimal faixaMin,
        BigDecimal faixaMax,
        String     source
) {
    /** Constrói resposta a partir de um AiSuggestion do serviço. */
    public static AiSuggestResponse fromAi(AiSuggestion s) {
        return new AiSuggestResponse(s.descricaoSugerida(), s.faixaMin(), s.faixaMax(), "AI");
    }

    /** Resposta de fallback manual — IA indisponível. */
    public static AiSuggestResponse fallback() {
        return new AiSuggestResponse(null, null, null, "FALLBACK_MANUAL");
    }
}
