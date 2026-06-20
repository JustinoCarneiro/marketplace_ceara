package com.onda.marketplace.proposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProposalDto(
        UUID       id,
        UUID       serviceRequestId,
        UUID       prestadorId,
        BigDecimal valor,
        int        prazoDias,
        String     status,
        Instant    createdAt
) {
    static ProposalDto from(Proposal p) {
        return new ProposalDto(
                p.getId(),
                p.getServiceRequest().getId(),
                p.getPrestadorId(),
                p.getValor(),
                p.getPrazoDias(),
                p.getStatus().name(),
                p.getCreatedAt());
    }
}
