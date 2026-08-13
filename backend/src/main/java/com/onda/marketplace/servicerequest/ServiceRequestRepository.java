package com.onda.marketplace.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {
    /**
     * Idempotência escopada ao cliente — a chave é do solicitante, não global. Buscar só
     * por chave devolvia o pedido de OUTRO cliente quando as chaves colidiam (V14).
     */
    Optional<ServiceRequest> findByIdempotencyKeyAndCliente_Id(String idempotencyKey, UUID clienteId);
    Optional<ServiceRequest> findByIdAndCliente_Id(UUID id, UUID clienteId);

    // Métricas/alertas do painel admin (US23/US30)
    long countByStatus(ServiceRequestStatus status);

    // Fila de disputas (US24)
    java.util.List<ServiceRequest> findByStatus(ServiceRequestStatus status);

    // "Meus pedidos" do cliente no app (mobile)
    java.util.List<ServiceRequest> findByClienteIdOrderByUpdatedAtDesc(UUID clienteId);

    // Fila aberta do prestador no app (mobile)
    java.util.List<ServiceRequest> findByStatusOrderByCreatedAtDesc(ServiceRequestStatus status);

    @Query("SELECT s.cliente.id FROM ServiceRequest s WHERE s.id = :srId")
    Optional<UUID> findClienteIdBySrId(@Param("srId") UUID srId);

    /**
     * Pedidos agrupados por status dentro do período (US23). Uma consulta só resolve
     * o gráfico, o total e a taxa de conclusão do dashboard.
     * Retorna linhas [ServiceRequestStatus, Long].
     */
    @Query("""
           SELECT s.status, COUNT(s) FROM ServiceRequest s
            WHERE s.createdAt >= :de AND s.createdAt < :ate
            GROUP BY s.status
           """)
    java.util.List<Object[]> contarPorStatusNoPeriodo(@Param("de") java.time.Instant de,
                                                      @Param("ate") java.time.Instant ate);

    // Mesma métrica, recortada por bairro (US23 parte 2) — método separado em vez de
    // "(:bairro IS NULL OR ...)" seguindo o mesmo motivo já documentado acima pra datas:
    // evitar depender de inferência de tipo de parâmetro nulo solto.
    @Query("""
           SELECT s.status, COUNT(s) FROM ServiceRequest s
            WHERE s.createdAt >= :de AND s.createdAt < :ate AND s.bairro = :bairro
            GROUP BY s.status
           """)
    java.util.List<Object[]> contarPorStatusNoPeriodoEBairro(@Param("de") java.time.Instant de,
                                                             @Param("ate") java.time.Instant ate,
                                                             @Param("bairro") String bairro);

    // Lista de bairros distintos já usados em algum pedido — popula o seletor do admin sem
    // precisar manter uma tabela separada de bairros válidos.
    @Query("SELECT DISTINCT s.bairro FROM ServiceRequest s WHERE s.bairro IS NOT NULL ORDER BY s.bairro")
    java.util.List<String> bairrosDistintos();

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
