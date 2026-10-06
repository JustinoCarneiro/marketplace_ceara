package com.onda.marketplace.auth;

import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Tudo o que a exclusão de conta (US36) consulta e apaga, num lugar só: quem lê aqui vê a política
 * inteira — o que impede a exclusão, o que sai e, por omissão, o que fica (transações, pedidos
 * concluídos, avaliações sem o comentário, SOS, denúncias, aceite de termos, trilha de auditoria).
 * As consultas atravessam várias tabelas, por isso não ficam espalhadas pelos repositórios de cada
 * módulo; e, sendo {@code @Query}, um erro de JPQL derruba a subida do app em vez de estourar só no
 * dia de uma exclusão real.
 *
 * <p>Os UPDATE/DELETE em lote não tocam em {@code users} nem em {@code providers_profile}: as únicas
 * entidades que o serviço carrega seguem consistentes com o banco, sem limpar o contexto de persistência.
 */
public interface AccountDeletionRepository extends Repository<User, UUID> {

    // ---------- o que impede a exclusão (só leitura) ----------

    /** O cliente tem pedido em algum destes status? (aceito/em andamento/em disputa = outra parte esperando) */
    @Query("""
           SELECT CASE WHEN EXISTS (
               SELECT 1 FROM ServiceRequest s
                WHERE s.cliente.id = :userId AND s.status IN :statuses
           ) THEN true ELSE false END
           """)
    boolean clienteTemPedidoEm(@Param("userId") UUID userId,
                               @Param("statuses") Collection<ServiceRequestStatus> statuses);

    /** Pedido cancelado do cliente com o dinheiro ainda retido: o reembolso está a caminho. */
    @Query("""
           SELECT CASE WHEN EXISTS (
               SELECT 1 FROM Transaction t, ServiceRequest s
                WHERE s.id = t.serviceRequestId AND s.cliente.id = :userId
                  AND s.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.CANCELADO
                  AND t.statusPagamento = com.onda.marketplace.payment.TransactionStatus.RETIDO
           ) THEN true ELSE false END
           """)
    boolean clienteTemReembolsoPendente(@Param("userId") UUID userId);

    /** O prestador tem serviço (proposta aceita) em pedido em algum destes status? */
    @Query("""
           SELECT CASE WHEN EXISTS (
               SELECT 1 FROM Proposal p
                WHERE p.prestadorId = :userId
                  AND p.status = com.onda.marketplace.proposal.ProposalStatus.ACEITA
                  AND p.serviceRequest.status IN :statuses
           ) THEN true ELSE false END
           """)
    boolean prestadorTemServicoEm(@Param("userId") UUID userId,
                                  @Param("statuses") Collection<ServiceRequestStatus> statuses);

    /**
     * Serviço concluído pelo prestador com o dinheiro ainda retido: falta o repasse. É a fila de repasse
     * manual (Modelo A) — o admin precisa da chave Pix dele para pagar; apagá-la deixaria o dinheiro sem destino.
     */
    @Query("""
           SELECT CASE WHEN EXISTS (
               SELECT 1 FROM Transaction t, Proposal p
                WHERE p.serviceRequest.id = t.serviceRequestId
                  AND p.prestadorId = :userId
                  AND p.status = com.onda.marketplace.proposal.ProposalStatus.ACEITA
                  AND p.serviceRequest.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.CONCLUIDO
                  AND t.statusPagamento = com.onda.marketplace.payment.TransactionStatus.RETIDO
           ) THEN true ELSE false END
           """)
    boolean prestadorTemRepasseAReceber(@Param("userId") UUID userId);

    // ---------- o que sai ----------

    /**
     * Pedidos PROPOSTO em que a ÚNICA proposta ativa é deste prestador voltam à fila (PENDENTE): sem isso ficariam presos
     * em PROPOSTO, invisíveis aos outros prestadores. Roda ANTES de encerrar as propostas dele (é por elas que acha os pedidos).
     */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s
              SET s.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.PENDENTE, s.updatedAt = :agora
            WHERE s.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.PROPOSTO
              AND EXISTS (SELECT 1 FROM Proposal p WHERE p.serviceRequest.id = s.id AND p.prestadorId = :userId
                             AND p.status = com.onda.marketplace.proposal.ProposalStatus.ATIVA)
              AND NOT EXISTS (SELECT 1 FROM Proposal q WHERE q.serviceRequest.id = s.id AND q.prestadorId <> :userId
                                 AND q.status = com.onda.marketplace.proposal.ProposalStatus.ATIVA)
           """)
    int reabrirPedidosSoComPropostaDoPrestador(@Param("userId") UUID userId, @Param("agora") Instant agora);

    /** Propostas ainda abertas do prestador (em pedidos de outros clientes): ninguém mais pode aceitá-las. */
    @Modifying
    @Query("""
           UPDATE Proposal p SET p.status = com.onda.marketplace.proposal.ProposalStatus.ENCERRADA
            WHERE p.prestadorId = :userId
              AND p.status = com.onda.marketplace.proposal.ProposalStatus.ATIVA
           """)
    int encerrarPropostasAtivasDoPrestador(@Param("userId") UUID userId);

    /** Propostas ainda abertas nos pedidos do cliente: depois da exclusão nada dele fica visível a prestadores. */
    @Modifying
    @Query("""
           UPDATE Proposal p SET p.status = com.onda.marketplace.proposal.ProposalStatus.ENCERRADA
            WHERE p.status = com.onda.marketplace.proposal.ProposalStatus.ATIVA
              AND p.serviceRequest.id IN (SELECT s.id FROM ServiceRequest s WHERE s.cliente.id = :userId)
           """)
    int encerrarPropostasAtivasDosPedidosDoCliente(@Param("userId") UUID userId);

    /** Pedidos sem compromisso (pendente/proposto) saem da fila dos prestadores. {@code agora}: o UPDATE em lote não roda o @PreUpdate. */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s
              SET s.status = com.onda.marketplace.servicerequest.ServiceRequestStatus.CANCELADO,
                  s.updatedAt = :agora
            WHERE s.cliente.id = :userId
              AND s.status IN (com.onda.marketplace.servicerequest.ServiceRequestStatus.PENDENTE,
                               com.onda.marketplace.servicerequest.ServiceRequestStatus.PROPOSTO)
           """)
    int cancelarPedidosSemCompromissoDoCliente(@Param("userId") UUID userId, @Param("agora") Instant agora);

    /**
     * Texto livre e localização dos pedidos do cliente. Fica o que é do histórico e não identifica ninguém:
     * categoria, status, bairro (região ampla, usada nos relatórios), valores e datas.
     *
     * <p>{@code motivoDisputa} entrou junto com {@code detalhesDisputa} (achado da revisão cruzada,
     * 2026-10-05): a API aceita motivo como texto livre, e um pedido disputado — depois mediado — segue
     * elegível à exclusão (a recusa por pendência só olha {@code EM_DISPUTA} em curso, não resolvida).
     */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s
              SET s.descricao = NULL, s.aiDescricaoSugerida = NULL,
                  s.motivoDisputa = NULL, s.detalhesDisputa = NULL, s.localizacao = NULL
            WHERE s.cliente.id = :userId
           """)
    int apagarDadosPessoaisDosPedidosDoCliente(@Param("userId") UUID userId);

    /**
     * O mesmo texto de disputa, mas em pedidos onde o usuário excluído é o PRESTADOR (proposta aceita) —
     * não o cliente. {@code openDispute} aceita qualquer das duas partes; sem isto, a disputa que um
     * prestador escreveu sobre o próprio atendimento nunca saía da base, mesmo excluindo a conta dele.
     */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s
              SET s.motivoDisputa = NULL, s.detalhesDisputa = NULL
            WHERE EXISTS (
               SELECT 1 FROM Proposal p
                WHERE p.serviceRequest.id = s.id
                  AND p.prestadorId = :userId
                  AND p.status = com.onda.marketplace.proposal.ProposalStatus.ACEITA
            )
           """)
    int apagarMotivoDeDisputaDosPedidosOndeEhPrestador(@Param("userId") UUID userId);

    /**
     * Bairro fora da lista fixa ({@link com.onda.marketplace.shared.Bairro#VALIDOS}) é texto livre que a
     * validação de entrada passou a barrar — mas pode ter entrado antes dela existir. Sanado aqui junto
     * com o resto, não só bloqueado daqui pra frente (achado da revisão cruzada, 2026-10-05).
     */
    @Modifying
    @Query("""
           UPDATE ServiceRequest s SET s.bairro = NULL
            WHERE s.cliente.id = :userId AND s.bairro IS NOT NULL AND s.bairro NOT IN :validos
           """)
    int sanearBairroForaDaListaDosPedidosDoCliente(@Param("userId") UUID userId,
                                                    @Param("validos") Collection<String> validos);

    /** Fotos e áudios dos pedidos do cliente (a casa dele, a voz dele). Só a URL fica no banco. */
    @Modifying
    @Query("""
           DELETE FROM ServiceMedia m
            WHERE m.serviceRequest.id IN (SELECT s.id FROM ServiceRequest s WHERE s.cliente.id = :userId)
           """)
    int apagarMidiaDosPedidosDoCliente(@Param("userId") UUID userId);

    /** O texto sai; a linha fica (quem falou, quando) para a conversa da outra parte não ficar com buracos. */
    @Modifying
    @Query("""
           UPDATE Message m SET m.conteudo = '[mensagem removida]', m.mascarado = false
            WHERE m.remetenteId = :userId
           """)
    int removerConteudoDasMensagensDoUsuario(@Param("userId") UUID userId);

    /** O comentário sai; a nota fica — é reputação do avaliado, não dado pessoal do avaliador. */
    @Modifying
    @Query("UPDATE Review r SET r.comentario = NULL WHERE r.avaliadorId = :userId")
    int removerComentariosDasAvaliacoesDoUsuario(@Param("userId") UUID userId);

    /** Encerra todas as sessões abertas (refresh tokens). */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user.id = :userId")
    int apagarSessoesDoUsuario(@Param("userId") UUID userId);

    /** Códigos de recuperação de senha (só o HMAC existe no banco, mas são do usuário e não servem mais). */
    @Modifying
    @Query("DELETE FROM PasswordResetCode c WHERE c.userId = :userId")
    int apagarCodigosDeRecuperacaoDoUsuario(@Param("userId") UUID userId);
}
