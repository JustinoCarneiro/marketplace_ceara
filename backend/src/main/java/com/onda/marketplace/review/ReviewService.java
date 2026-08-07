package com.onda.marketplace.review;

import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ReviewService {

    private final ReviewRepository          reviewRepository;
    private final ServiceRequestRepository  srRepository;
    private final ProposalRepository        proposalRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final long                      prazoRevelacaoDias;

    public ReviewService(ReviewRepository reviewRepository,
                         ServiceRequestRepository srRepository,
                         ProposalRepository proposalRepository,
                         ProviderProfileRepository providerProfileRepository,
                         @Value("${marketplace.review.reveal-deadline-days:14}") long prazoRevelacaoDias) {
        this.reviewRepository          = reviewRepository;
        this.srRepository              = srRepository;
        this.proposalRepository        = proposalRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.prazoRevelacaoDias        = prazoRevelacaoDias;
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

        // Double-blind: nasce oculta. Só vira pública quando a contraparte avalia
        // (abaixo) ou quando o prazo vence (ReviewRevealJob).
        Review review = new Review(srId, avaliadorId, avaliadoId, tipo, req.nota(), req.comentario());
        reviewRepository.save(review);

        ReviewType contraparte = tipo == ReviewType.CLIENTE_AVALIA_PRESTADOR
                ? ReviewType.PRESTADOR_AVALIA_CLIENTE
                : ReviewType.CLIENTE_AVALIA_PRESTADOR;

        reviewRepository.findByServiceRequestIdAndTipo(srId, contraparte).ifPresent(outra ->
                revelar(List.of(review, outra)));

        return ReviewDto.from(review, prazoRevelacao(review));
    }

    /**
     * Revela as avaliações informadas e recalcula a reputação de quem foi avaliado.
     * O recálculo é dirigido pela revelação (não pelo tipo da avaliação recém-criada):
     * quando o prestador avalia por último, é a avaliação DELE que dispara a
     * revelação da nota que o cliente havia dado — e é essa que muda a média.
     */
    @Transactional
    public void revelar(Collection<Review> reviews) {
        List<Review> ocultas = reviews.stream().filter(r -> !r.isRevelada()).toList();
        if (ocultas.isEmpty()) return;

        ocultas.forEach(Review::revelar);
        reviewRepository.saveAll(ocultas);

        ocultas.stream()
                .filter(r -> r.getTipo() == ReviewType.CLIENTE_AVALIA_PRESTADOR)
                .map(Review::getAvaliadoId)
                .distinct()
                .forEach(this::atualizarNotaMedia);
    }

    /** Avaliações ocultas cujo prazo de reciprocidade venceu. */
    @Transactional(readOnly = true)
    public List<Review> buscarVencidas(Instant agora) {
        return reviewRepository.findByReveladaFalseAndCriadoEmBefore(
                agora.minus(Duration.ofDays(prazoRevelacaoDias)));
    }

    /** Quando a avaliação vira pública mesmo sem a contraparte responder. */
    public Instant prazoRevelacao(Review r) {
        return r.isRevelada() ? r.getReveladaEm()
                              : r.getCriadoEm().plus(Duration.ofDays(prazoRevelacaoDias));
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
