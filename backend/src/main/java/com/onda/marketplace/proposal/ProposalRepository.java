package com.onda.marketplace.proposal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByServiceRequestId(UUID serviceRequestId);
    List<Proposal> findByServiceRequestIdAndStatus(UUID serviceRequestId, ProposalStatus status);
}
