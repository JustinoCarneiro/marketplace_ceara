package com.onda.marketplace.proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proposta enviada ao cliente (US16 — tela de comparação de propostas).
 *
 * <p>Inclui nome e reputação do prestador porque comparar propostas é decidir entre
 * <b>pessoas</b>, não só entre preços: sem esses campos a tela caía num "Prestador"
 * genérico com nota 0,0 para todo mundo. {@code prestadorNota}/{@code prestadorAvaliacoes}
 * contam apenas avaliações já reveladas (double-blind, US31).
 */
public record ProposalDto(
        UUID       id,
        UUID       serviceRequestId,
        UUID       prestadorId,
        String     prestadorNome,
        BigDecimal prestadorNota,
        int        prestadorAvaliacoes,
        BigDecimal valor,
        int        prazoDias,
        Instant    horarioProposto,
        String     status,
        Instant    createdAt
) {
    static ProposalDto from(Proposal p, String prestadorNome,
                            BigDecimal prestadorNota, int prestadorAvaliacoes) {
        return new ProposalDto(
                p.getId(),
                p.getServiceRequest().getId(),
                p.getPrestadorId(),
                prestadorNome,
                prestadorNota,
                prestadorAvaliacoes,
                p.getValor(),
                p.getPrazoDias(),
                p.getHorarioProposto(),
                p.getStatus().name(),
                p.getCreatedAt());
    }
}
