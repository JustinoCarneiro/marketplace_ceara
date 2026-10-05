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
        ServiceRequest sr = requestRepository.findById(serviceRequestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        // Conta única com papéis: o prestador pode ser também o cliente deste pedido. Ninguém contrata a si mesmo (fabricaria
        // reputação); o aceite recusa o mesmo caso, mas aqui a proposta nem chega a existir.
        if (sr.getCliente().getId().equals(prestadorId)) {
            throw new BusinessException("SELF_HIRE_FORBIDDEN", "Você não pode enviar proposta ao seu próprio pedido.");
        }

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
        Proposal proposal = findAtiva(proposalId);
        ServiceRequest sr = proposal.getServiceRequest();

        // Sem isto, qualquer conta autenticada aceitava proposta de pedido alheio —
        // clienteId nunca era conferido contra o dono real do pedido.
        if (!clienteId.equals(sr.getCliente().getId())) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        if (clienteId.equals(proposal.getPrestadorId())) {
            throw new BusinessException("SELF_HIRE_FORBIDDEN",
                    "Prestador não pode aceitar o próprio pedido.");
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
        Proposal proposal = findAtiva(proposalId);

        // Antes, clienteId chegava até aqui e nunca era usado — qualquer conta autenticada
        // recusava proposta de prestador em pedido alheio.
        if (!clienteId.equals(proposal.getServiceRequest().getCliente().getId())) {
            throw new BusinessException("FORBIDDEN", "Você não participa deste pedido.");
        }

        proposal.recusar();
        proposalRepository.save(proposal);
        reabrirSeNaoHaPropostaAtiva(proposal.getServiceRequest());
        return toDto(proposal);
    }

    /**
     * A fila dos prestadores só lista PENDENTE, então um pedido PROPOSTO sem proposta ativa ficava preso e invisível
     * (e o cliente não tinha como cancelá-lo). Recusada a última proposta ativa, ele volta à fila. Recusar UMA de várias
     * não mexe em nada: as outras continuam disputando.
     */
    private void reabrirSeNaoHaPropostaAtiva(ServiceRequest sr) {
        if (sr.getStatus() == ServiceRequestStatus.PROPOSTO
                && proposalRepository.findByServiceRequestIdAndStatus(sr.getId(), ProposalStatus.ATIVA).isEmpty()) {
            sr.setStatus(ServiceRequestStatus.PENDENTE);
            requestRepository.save(sr);
        }
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
