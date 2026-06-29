package com.onda.marketplace.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    Optional<ServiceRequest> findByIdempotencyKey(String idempotencyKey);
    Optional<ServiceRequest> findByIdAndCliente_Id(UUID id, UUID clienteId);

    // Métricas/alertas do painel admin (US23/US30)
    long countByStatus(ServiceRequestStatus status);

    // Fila de disputas (US24)
    java.util.List<ServiceRequest> findByStatus(ServiceRequestStatus status);

    @Query("SELECT s.cliente.id FROM ServiceRequest s WHERE s.id = :srId")
    Optional<UUID> findClienteIdBySrId(@Param("srId") UUID srId);

    // Participação: verifica se o user é cliente OU prestador (via proposta aceita) do pedido
    @Query("""
        SELECT CASE WHEN EXISTS(
            SELECT 1 FROM ServiceRequest s WHERE s.id = :srId AND s.cliente.id = :userId
        ) OR EXISTS(
            SELECT 1 FROM Proposal p WHERE p.serviceRequest.id = :srId
                AND p.prestadorId = :userId AND p.status = 'ACEITA'
        ) THEN true ELSE false END
        """)
    boolean isParticipante(@Param("srId") UUID srId, @Param("userId") UUID userId);
}
