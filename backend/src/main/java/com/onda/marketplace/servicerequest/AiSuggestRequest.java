package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request para o endpoint de sugestão por IA (US14 / M04).
 * O campo {@code descricao} pode ser nulo (o usuário pode enviar só mídia).
 */
public record AiSuggestRequest(
        String descricao,
        List<String> mediaUrls
) {}
