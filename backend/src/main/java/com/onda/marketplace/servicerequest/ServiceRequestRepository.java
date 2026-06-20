package com.onda.marketplace.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);
    Optional<ServiceRequest> findByIdAndCliente_Id(UUID id, UUID clienteId);

    @Query("SELECT s.cliente.id FROM ServiceRequest s WHERE s.id = :srId")
    Optional<UUID> findClienteIdBySrId(@Param("srId") UUID srId);
}
