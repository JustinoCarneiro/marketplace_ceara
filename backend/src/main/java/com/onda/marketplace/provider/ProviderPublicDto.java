package com.onda.marketplace.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Perfil público do prestador visto pelo cliente (ProviderProfileScreen).
 * NUNCA expõe CPF nem e-mail (TS04/LGPD) — só o que sustenta a decisão de contratar.
 */
public record ProviderPublicDto(
        UUID       userId,
        String     nome,
        String     categoria,
        String     bio,
        BigDecimal notaMedia,
        int        totalAvaliacoes,
        int        totalServicos,
        String     statusVerificacao,
        List<Avaliacao> avaliacoes
) {
    public record Avaliacao(String autorNome, int nota, String comentario) {}
}
