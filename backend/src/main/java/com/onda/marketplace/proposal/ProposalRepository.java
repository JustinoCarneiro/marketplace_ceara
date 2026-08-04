package com.onda.marketplace.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByServiceRequestId(UUID serviceRequestId);
    List<Proposal> findByServiceRequestIdAndStatus(UUID serviceRequestId, ProposalStatus status);

    // Serviços efetivamente contratados do prestador — perfil público no app (mobile)
    long countByPrestadorIdAndStatus(UUID prestadorId, ProposalStatus status);

    // Atendimento ativo do prestador (tab "Em Andamento" no app) — 1 prestador, 1 proposta
    // ACEITA por vez no MVP (sem múltiplos atendimentos simultâneos).
    List<Proposal> findByPrestadorIdAndStatus(UUID prestadorId, ProposalStatus status);
}
