package com.onda.marketplace.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeResolutionRepository extends JpaRepository<DisputeResolution, UUID> {
}
