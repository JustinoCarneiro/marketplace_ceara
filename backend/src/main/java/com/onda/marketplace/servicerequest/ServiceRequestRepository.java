package com.onda.marketplace.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);
    Optional<ServiceRequest> findByIdAndCliente_Id(UUID id, UUID clienteId);
}
