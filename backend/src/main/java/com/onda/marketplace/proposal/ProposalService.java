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

        if (sr.getStatus() == ServiceRequestStatus.CANCELADO
                || sr.getStatus() == ServiceRequestStatus.CONCLUIDO) {
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

        if (clienteId.equals(proposal.getPrestadorId())) {
            throw new BusinessException("SELF_HIRE_FORBIDDEN",
                    "Prestador não pode aceitar o próprio pedido.");
        }

        ServiceRequest sr = proposal.getServiceRequest();

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
