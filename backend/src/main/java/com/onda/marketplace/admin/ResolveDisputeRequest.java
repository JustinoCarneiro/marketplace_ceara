package com.onda.marketplace.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo da resolução de disputa. A justificativa é obrigatória — registrada
 * no log de auditoria imutável ({@link DisputeResolution}).
 */
public record ResolveDisputeRequest(
        @NotNull  MediationDecision decisao,
        @NotBlank String justificativa
) {}
