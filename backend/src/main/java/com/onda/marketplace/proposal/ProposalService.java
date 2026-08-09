package com.onda.marketplace.proposal;

import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ProposalService {

    private final ProposalRepository       proposalRepository;
    private final ServiceRequestRepository requestRepository;

    public ProposalService(ProposalRepository proposalRepository,
                           ServiceRequestRepository requestRepository) {
        this.proposalRepository = proposalRepository;
        this.requestRepository  = requestRepository;
    }

    @Transactional
    public ProposalDto create(UUID serviceRequestId, CreateProposalRequest req, UUID prestadorId) {
        ServiceRequest sr = requestRepository.findById(serviceRequestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        // Só PENDENTE/PROPOSTO aceitam proposta nova — ACEITO/EM_ANDAMENTO/EM_DISPUTA já têm
        // prestador definido; sem este check dava pra empilhar proposta ATIVA num serviço em
        // execução (US15).
        if (sr.getStatus() != ServiceRequestStatus.PENDENTE
                && sr.getStatus() != ServiceRequestStatus.PROPOSTO) {
            throw new BusinessException("REQUEST_CLOSED", "Pedido não aceita mais propostas.");
        }

        var proposal = new Proposal(sr, prestadorId, req.valor(), req.prazoDias(), ProposalStatus.ATIVA);
        proposalRepository.save(proposal);

        if (sr.getStatus() == ServiceRequestStatus.PENDENTE) {
            sr.setStatus(ServiceRequestStatus.PROPOSTO);
            requestRepository.save(sr);
        }

        return ProposalDto.from(proposal);
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

        return ProposalDto.from(proposal);
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
        return ProposalDto.from(proposal);
    }

    @Transactional(readOnly = true)
    public List<ProposalDto> listForRequest(UUID serviceRequestId) {
        return proposalRepository.findByServiceRequestId(serviceRequestId)
                .stream()
                .map(ProposalDto::from)
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
