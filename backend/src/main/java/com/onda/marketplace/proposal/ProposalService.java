package com.onda.marketplace.proposal;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderVerificationGuard;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ProposalService {

    private final ProposalRepository        proposalRepository;
    private final ServiceRequestRepository  requestRepository;
    private final UserRepository            userRepository;
    private final ProviderProfileRepository profileRepository;
    private final ReviewRepository          reviewRepository;
    private final ProviderVerificationGuard verificationGuard;

    public ProposalService(ProposalRepository proposalRepository,
                           ServiceRequestRepository requestRepository,
                           UserRepository userRepository,
                           ProviderProfileRepository profileRepository,
                           ReviewRepository reviewRepository,
                           ProviderVerificationGuard verificationGuard) {
        this.proposalRepository = proposalRepository;
        this.requestRepository  = requestRepository;
        this.userRepository     = userRepository;
        this.profileRepository  = profileRepository;
        this.reviewRepository   = reviewRepository;
        this.verificationGuard  = verificationGuard;
    }

    /**
     * Monta o DTO com nome/reputação do prestador. Ponto único de construção — se algum
     * caminho montasse o DTO sem enriquecer, a tela de comparação voltaria a mostrar
     * "Prestador"/0,0 só naquele fluxo, que é exatamente o bug que isto corrige.
     */
    private ProposalDto toDto(Proposal p) {
        UUID prestadorId = p.getPrestadorId();
        String nome = userRepository.findById(prestadorId)
                .map(User::getNome)
                .orElse(null);
        BigDecimal nota = profileRepository.findByUserId(prestadorId)
                .map(ProviderProfile::getNotaMedia)
                .orElse(null);
        int avaliacoes = (int) reviewRepository.countByAvaliadoIdAndTipoAndReveladaTrue(
                prestadorId, ReviewType.CLIENTE_AVALIA_PRESTADOR);
        return ProposalDto.from(p, nome, nota, avaliacoes);
    }

    @Transactional
    public ProposalDto create(UUID serviceRequestId, CreateProposalRequest req, UUID prestadorId) {
        // Trava de escrita (revisão cruzada, 2026-10-05): serializa contra a reabertura de reject() —
        // ver o porquê em ServiceRequestRepository.findByIdComTrava.
        ServiceRequest sr = requestRepository.findByIdComTrava(serviceRequestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        // Só PENDENTE/PROPOSTO aceitam proposta nova — ACEITO/EM_ANDAMENTO/EM_DISPUTA já têm
        // prestador definido; sem este check dava pra empilhar proposta ATIVA num serviço em
        // execução (US15).
        if (sr.getStatus() != ServiceRequestStatus.PENDENTE
                && sr.getStatus() != ServiceRequestStatus.PROPOSTO) {
            throw new BusinessException("REQUEST_CLOSED", "Pedido não aceita mais propostas.");
        }

        // Só prestador VERIFICADO propõe: antes da recusa nada é gravado (nem a proposta, nem a
        // transição PENDENTE → PROPOSTO).
        verificationGuard.exigirVerificado(prestadorId);

        var proposal = new Proposal(sr, prestadorId, req.valor(), req.prazoDias(),
                req.horarioProposto(), ProposalStatus.ATIVA);
        proposalRepository.save(proposal);

        if (sr.getStatus() == ServiceRequestStatus.PENDENTE) {
            sr.setStatus(ServiceRequestStatus.PROPOSTO);
            requestRepository.save(sr);
        }

        return toDto(proposal);
    }

    @Transactional
    public ProposalDto accept(UUID proposalId, UUID clienteId) {
        // Pedido travado ANTES de qualquer outra leitura (revisão cruzada, 2ª rodada): serializa o aceite com create()
        // (uma proposta nova entre a consulta das ATIVAS e o aceite ficava ATIVA, órfã, num pedido ACEITO — e um 2º accept
        // a aceitava também), com cancel() e com reject(). Sem a trava o save abaixo ainda sobrescrevia um cancelamento
        // que commitasse no meio: o pedido cancelado e já reembolsado voltava a ACEITO.
        ServiceRequest sr = travarPedidoDaProposta(proposalId);
        Proposal proposal = findAtiva(proposalId);

        // Sem isto, qualquer conta autenticada aceitava proposta de pedido alheio —
        // clienteId nunca era conferido contra o dono real do pedido.
        if (!clienteId.equals(sr.getCliente().getId())) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        if (clienteId.equals(proposal.getPrestadorId())) {
            throw new BusinessException("SELF_HIRE_FORBIDDEN",
                    "Prestador não pode aceitar o próprio pedido.");
        }

        // Aceitar só faz sentido de PROPOSTO: antes não havia checagem de estado nenhuma, e o save sobrescrevia qualquer outro.
        if (sr.getStatus() != ServiceRequestStatus.PROPOSTO) {
            throw new BusinessException("INVALID_STATE_TRANSITION",
                    "accept() exige status PROPOSTO, atual: " + sr.getStatus());
        }

        // O status pode ter mudado depois da proposta (o admin reprova ou suspende): recusa antes de
        // qualquer efeito, para o cliente não pagar um prestador já barrado.
        verificationGuard.exigirContratavel(proposal.getPrestadorId());

        proposalRepository.findByServiceRequestIdAndStatus(sr.getId(), ProposalStatus.ATIVA)
                .stream()
                .filter(p -> p != proposal)   // identidade: mesma sessão JPA garante mesmo objeto
                .forEach(p -> {
                    p.encerrar();
                    proposalRepository.save(p);
                });

        proposal.aceitar();
        proposalRepository.save(proposal);

        sr.setStatus(ServiceRequestStatus.ACEITO);
        requestRepository.save(sr);

        return toDto(proposal);
    }

    @Transactional
    public ProposalDto reject(UUID proposalId, UUID clienteId) {
        // Pedido travado ANTES de qualquer outra leitura (ver accept): é o que faz a conferência de reabrirSeNaoHaPropostaAtiva
        // valer — o status do pedido lido aqui é o de agora, não o de antes de uma transação concorrente commitar.
        ServiceRequest sr = travarPedidoDaProposta(proposalId);
        Proposal proposal = findAtiva(proposalId);

        // Antes, clienteId chegava até aqui e nunca era usado — qualquer conta autenticada
        // recusava proposta de prestador em pedido alheio.
        if (!clienteId.equals(sr.getCliente().getId())) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        proposal.recusar();
        proposalRepository.save(proposal);
        reabrirSeNaoHaPropostaAtiva(sr);
        return toDto(proposal);
    }

    /**
     * A fila dos prestadores só lista PENDENTE, então um pedido PROPOSTO sem proposta ativa ficava preso e invisível
     * (e o cliente não tinha como cancelá-lo). Recusada a última proposta ativa, ele volta à fila. Recusar UMA de várias
     * não mexe em nada: as outras continuam disputando.
     *
     * <p>{@code sr} vem de {@link #travarPedidoDaProposta}: lido com trava como a PRIMEIRA leitura do pedido, serializa com
     * {@code create()} (uma proposta nova não some da fila) e com {@code cancel()} (um pedido cancelado no meio não volta
     * a PENDENTE: o status aqui é o de agora). A versão anterior relia o pedido "sob a trava" depois de já tê-lo carregado
     * na checagem de posse — o Hibernate devolve a instância antiga numa releitura, então o status conferido podia ser o de antes.
     */
    private void reabrirSeNaoHaPropostaAtiva(ServiceRequest sr) {
        if (sr.getStatus() == ServiceRequestStatus.PROPOSTO
                && proposalRepository.findByServiceRequestIdAndStatus(sr.getId(), ProposalStatus.ATIVA).isEmpty()) {
            sr.setStatus(ServiceRequestStatus.PENDENTE);
            requestRepository.save(sr);
        }
    }

    /**
     * O pedido da proposta, com trava de escrita, como a PRIMEIRA leitura dele na transação. O id sai de uma consulta escalar,
     * que não carrega a entidade: carregar a proposta antes já traria o pedido (a checagem de posse o usa), e sobre uma
     * entidade já carregada {@code findByIdComTrava} devolve a mesma instância com o estado antigo.
     */
    private ServiceRequest travarPedidoDaProposta(UUID proposalId) {
        UUID srId = proposalRepository.findServiceRequestIdById(proposalId)
                .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposta não encontrada."));
        return requestRepository.findByIdComTrava(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<ProposalDto> listForRequest(UUID serviceRequestId) {
        return proposalRepository.findByServiceRequestId(serviceRequestId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private Proposal findAtiva(UUID proposalId) {
        Proposal p = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND", "Proposta não encontrada."));
        if (p.getStatus() != ProposalStatus.ATIVA) {
            throw new BusinessException("PROPOSAL_NOT_ACTIVE", "Proposta não está ativa.");
        }
        return p;
    }
}
