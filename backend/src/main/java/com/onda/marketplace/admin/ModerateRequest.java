package com.onda.marketplace.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code justificativa}: texto livre do admin que vai para o log de auditoria (campo {@code detalhe}, TEXT). Sem teto, qualquer tamanho entrava
 * no banco e aparecia inteiro na página de Auditoria (achado da rodada 3); 500 caracteres cobrem uma justificativa de verdade.
 */
public record ModerateRequest(@NotNull ModerationAction action, @Size(max = 500) String justificativa) {}
