package com.onda.marketplace.servicerequest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    // E-mail do cliente para a cobrança Pix no gateway (payer.email do Mercado Pago).
    // Consulta escalar: o gateway é chamado fora de @Transactional (OutboxProcessor),
    // então nada de navegar lazy pela entidade — TS02.
    @Query("SELECT s.cliente.email FROM ServiceRequest s WHERE s.id = :srId")
    Optional<String> findClienteEmailBySrId(@Param("srId") UUID srId);

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

    // Exportação CSV (US29): as linhas do período, mesma faixa fechada das métricas e na
    // ordem em que foram criadas (o arquivo sai igual em duas exportações seguidas).
    @Query("""
           SELECT s FROM ServiceRequest s
            WHERE s.createdAt >= :de AND s.createdAt < :ate
            ORDER BY s.createdAt, s.id
           """)
    java.util.List<ServiceRequest> listarNoPeriodo(@Param("de") java.time.Instant de,
                                                   @Param("ate") java.time.Instant ate);

    @Query("""
           SELECT s FROM ServiceRequest s
            WHERE s.createdAt >= :de AND s.createdAt < :ate AND s.bairro = :bairro
            ORDER BY s.createdAt, s.id
           """)
    java.util.List<ServiceRequest> listarNoPeriodoEBairro(@Param("de") java.time.Instant de,
                                                          @Param("ate") java.time.Instant ate,
                                                          @Param("bairro") String bairro);

    // Lista de bairros distintos já usados em algum pedido — popula o seletor do admin sem
    // precisar manter uma tabela separada de bairros válidos.
    @Query("SELECT DISTINCT s.bairro FROM ServiceRequest s WHERE s.bairro IS NOT NULL ORDER BY s.bairro")
    java.util.List<String> bairrosDistintos();

    /**
     * Pedidos sem andamento desde {@code limite}: nenhuma mudança de estado e nenhuma proposta nova depois dele
     * (a proposta nova também é andamento: o cliente ainda está recebendo ofertas). Expiração de pedido sem prestador.
     */
    @Query("""
           SELECT s.id FROM ServiceRequest s
            WHERE s.status IN :statuses AND s.updatedAt < :limite
              AND NOT EXISTS (SELECT 1 FROM Proposal p WHERE p.serviceRequest.id = s.id AND p.createdAt >= :limite)
           """)
    List<UUID> idsSemAndamentoDesde(@Param("statuses") Collection<ServiceRequestStatus> statuses,
                                    @Param("limite") Instant limite);

    /**
     * O estado é conferido de novo na escrita, com a MESMA condição de {@link #idsSemAndamentoDesde} (status, prazo
     * e nenhuma proposta recente) — não só o status. Achado da revisão cruzada (2026-10-05): checar só o status
     * deixava passar uma corrida concreta — uma proposta nova chega entre a consulta (que montou {@code ids}) e
     * este UPDATE; ela reinicia o prazo (é andamento), mas só re-conferir o status não via isso, e o pedido
     * expirava apesar da proposta ser recente. Conferir os três de novo, na mesma transação da escrita, fecha a
     * corrida — um aceite ou uma proposta que chegou nesse intervalo não é desfeito.
     */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s
              SET s.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.CANCELADO, s.updatedAt = :agora
            WHERE s.id IN :ids
              AND s.status IN (com.onda.marketplace.servicerequest.ServiceRequestStatus.PENDENTE,
                               com.onda.marketplace.servicerequest.ServiceRequestStatus.PROPOSTO)
              AND s.updatedAt < :limite
              AND NOT EXISTS (SELECT 1 FROM Proposal p WHERE p.serviceRequest.id = s.id AND p.createdAt >= :limite)
           """)
    int cancelarSemAndamento(@Param("ids") Collection<UUID> ids, @Param("limite") Instant limite, @Param("agora") Instant agora);

    /**
     * Dentre {@code ids}, quais estão HOJE em {@code status} — lido DEPOIS do {@code cancelarSemAndamento} acima, na
     * MESMA transação (visibilidade das próprias escritas): são exatamente os que esta chamada acabou de cancelar,
     * nunca os que escaparam da corrida. É o que decide quais propostas de fato precisam ser encerradas.
     */
    @Query("SELECT s.id FROM ServiceRequest s WHERE s.id IN :ids AND s.status = :status")
    List<UUID> idsComStatus(@Param("ids") Collection<UUID> ids, @Param("status") ServiceRequestStatus status);

    /**
     * O mesmo princípio do {@code cancelarSemAndamento} acima, para o cancelamento pelo cliente/prestador
     * (achado da revisão cruzada, 2026-10-05): {@code ServiceExecutionService.cancel} lia o status, decidia se
     * cancelava, e só DEPOIS gravava — sem reler o estado na escrita. Entre a leitura e a gravação, o pedido podia
     * ter sido aceito e o pagamento iniciado numa transação concorrente; o {@code save()} do objeto em memória
     * sobrescrevia esse aceite com {@code CANCELADO} sem deixar rastro (nem erro, nem reembolso — a consulta por
     * transação RETIDA, logo depois, não achava nada porque ainda não tinha sido criada). Este {@code UPDATE}
     * confere o estado ATUAL na própria escrita: 0 linhas afetadas = o estado mudou nesse intervalo, e quem chama
     * tem de reler para decidir o que fazer, em vez de seguir como se tivesse cancelado.
     */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s
              SET s.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.CANCELADO, s.updatedAt = :agora
            WHERE s.id = :id AND s.status IN :estadosValidos
           """)
    int cancelarSeEmEstadoCancelavel(@Param("id") UUID id,
                                     @Param("estadosValidos") Collection<ServiceRequestStatus> estadosValidos,
                                     @Param("agora") Instant agora);

    /**
     * Leitura com trava de escrita na linha (revisão cruzada, 2026-10-05): serializa {@code ProposalService.create}
     * (proposta nova, PENDENTE→PROPOSTO) contra a reabertura de {@code reject} (recusada a ÚLTIMA proposta ativa,
     * PROPOSTO→PENDENTE). Sem a trava as duas liam o pedido sem se bloquear — cada uma reconferia status e propostas
     * ativas, mas via SELECT simples, nunca preso pelo banco: dava pra reabrir o pedido (PENDENTE) bem no instante em
     * que uma proposta nova (ATIVA) acabava de ser gravada para ele, escondendo-a da fila dos outros prestadores —
     * o pedido simplesmente não aparecia mais pra ninguém, sem erro nenhum.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ServiceRequest s WHERE s.id = :id")
    Optional<ServiceRequest> findByIdComTrava(@Param("id") UUID id);

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
