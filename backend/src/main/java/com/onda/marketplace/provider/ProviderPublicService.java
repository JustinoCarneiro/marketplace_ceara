package com.onda.marketplace.provider;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Perfil público do prestador consumido pelo app do cliente (US03/US08). */
@Service
@SuppressWarnings("null")
public class ProviderPublicService {

    private final ProviderProfileRepository profileRepository;
    private final ReviewRepository          reviewRepository;
    private final UserRepository            userRepository;
    private final ProposalRepository        proposalRepository;

    public ProviderPublicService(ProviderProfileRepository profileRepository,
                                 ReviewRepository reviewRepository,
                                 UserRepository userRepository,
                                 ProposalRepository proposalRepository) {
        this.profileRepository  = profileRepository;
        this.reviewRepository   = reviewRepository;
        this.userRepository     = userRepository;
        this.proposalRepository = proposalRepository;
    }

    @Transactional(readOnly = true)
    public ProviderPublicDto buscarPorUserId(UUID userId) {
        ProviderProfile p = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("PROVIDER_NOT_FOUND", "Prestador não encontrado."));

        List<Review> reviews = reviewRepository
                .findByAvaliadoIdAndTipoOrderByCriadoEmDesc(userId, ReviewType.CLIENTE_AVALIA_PRESTADOR);

        List<ProviderPublicDto.Avaliacao> avaliacoes = reviews.stream()
                .map(r -> new ProviderPublicDto.Avaliacao(
                        userRepository.findById(r.getAvaliadorId())
                                .map(u -> u.getNome())
                                .orElse("Cliente"),
                        r.getNota(),
                        r.getComentario()))
                .toList();

        return new ProviderPublicDto(
                p.getUser().getId(),
                p.getUser().getNome(),
                p.getCategoria(),
                p.getBio(),
                p.getNotaMedia(),
                reviews.size(),
                (int) proposalRepository.countByPrestadorIdAndStatus(userId, ProposalStatus.ACEITA),
                p.getStatusVerificacao().name(),
                avaliacoes);
    }
}
