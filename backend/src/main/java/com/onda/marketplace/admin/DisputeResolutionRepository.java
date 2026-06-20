package com.onda.marketplace.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeResolutionRepository extends JpaRepository<DisputeResolution, UUID> {
    // Detalhe de disputa — UNIQUE(service_request_id) garante no máximo 1 resultado
    java.util.Optional<DisputeResolution> findByServiceRequestId(UUID serviceRequestId);
}
