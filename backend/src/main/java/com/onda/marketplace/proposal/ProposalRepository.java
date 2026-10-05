package com.onda.marketplace.proposal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // Faixa de preço e tempo de resposta do perfil público — todas as propostas já
    // enviadas (não só ACEITA), porque refletem o padrão de cotação/resposta do
    // prestador, não só os serviços fechados.
    List<Proposal> findByPrestadorId(UUID prestadorId);

    /** Expiração de pedido sem andamento: as propostas ainda abertas deles se encerram junto. */
    @Modifying
    @Query("""
           UPDATE Proposal p SET p.status = com.onda.marketplace.proposal.ProposalStatus.ENCERRADA
            WHERE p.status = com.onda.marketplace.proposal.ProposalStatus.ATIVA
              AND p.serviceRequest.id IN :pedidos
           """)
    int encerrarAtivasDosPedidos(@Param("pedidos") Collection<UUID> pedidos);
}
