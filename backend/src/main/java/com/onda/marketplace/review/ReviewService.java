package com.onda.marketplace.review;

import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ReviewService {

    private final ReviewRepository          reviewRepository;
    private final ServiceRequestRepository  srRepository;
    private final ProposalRepository        proposalRepository;
    private final ProviderProfileRepository providerProfileRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         ServiceRequestRepository srRepository,
                         ProposalRepository proposalRepository,
                         ProviderProfileRepository providerProfileRepository) {
        this.reviewRepository          = reviewRepository;
        this.srRepository              = srRepository;
        this.proposalRepository        = proposalRepository;
        this.providerProfileRepository = providerProfileRepository;
    }

    @Transactional
    public ReviewDto avaliar(UUID srId, UUID avaliadorId, ReviewType tipo, AvaliarRequest req) {
        var sr = srRepository.findById(srId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "Pedido não encontrado."));

        if (sr.getStatus() != ServiceRequestStatus.CONCLUIDO) {
            throw new BusinessException("REVIEW_NOT_ALLOWED",
                    "Avaliação só é permitida em pedidos CONCLUIDOS.");
        }

        if (reviewRepository.existsByServiceRequestIdAndTipo(srId, tipo)) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS",
                    "Este pedido já foi avaliado para o tipo " + tipo);
        }

        UUID avaliadoId = resolverAvaliado(srId, tipo);

        Review review = new Review(srId, avaliadorId, avaliadoId, tipo, req.nota(), req.comentario());
        reviewRepository.save(review);

        if (tipo == ReviewType.CLIENTE_AVALIA_PRESTADOR) {
            atualizarNotaMedia(avaliadoId);
        }

        return ReviewDto.from(review);
    }

    private UUID resolverAvaliado(UUID srId, ReviewType tipo) {
        return switch (tipo) {
            case CLIENTE_AVALIA_PRESTADOR ->
                proposalRepository.findByServiceRequestIdAndStatus(srId, ProposalStatus.ACEITA)
                        .stream().findFirst()
                        .map(p -> p.getPrestadorId())
                        .orElseThrow(() -> new BusinessException("PROPOSAL_NOT_FOUND",
                                "Proposta aceita não encontrada."));
            case PRESTADOR_AVALIA_CLIENTE ->
                srRepository.findClienteIdBySrId(srId)
                        .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND",
                                "Cliente do pedido não encontrado."));
        };
    }

    private void atualizarNotaMedia(UUID prestadorId) {
        double media = reviewRepository.calcularMediaNota(prestadorId, ReviewType.CLIENTE_AVALIA_PRESTADOR);
        providerProfileRepository.findByUserId(prestadorId).ifPresent(profile -> {
            profile.setNotaMedia(BigDecimal.valueOf(media));
            providerProfileRepository.save(profile);
        });
    }
}
