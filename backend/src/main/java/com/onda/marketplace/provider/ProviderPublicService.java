package com.onda.marketplace.provider;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
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

        // Double-blind: só avaliações já reveladas entram no perfil público — as
        // ocultas também não contam na notaMedia, senão a média denunciaria a nota.
        List<Review> reviews = reviewRepository
                .findByAvaliadoIdAndTipoAndReveladaTrueOrderByCriadoEmDesc(
                        userId, ReviewType.CLIENTE_AVALIA_PRESTADOR);

        List<ProviderPublicDto.Avaliacao> avaliacoes = reviews.stream()
                .map(r -> new ProviderPublicDto.Avaliacao(
                        r.getId(),
                        userRepository.findById(r.getAvaliadorId())
                                .map(u -> u.getNome())
                                .orElse("Cliente"),
                        r.getNota(),
                        r.getComentario()))
                .toList();

        List<Proposal> propostas = proposalRepository.findByPrestadorId(userId);
        BigDecimal precoMin = propostas.stream().map(Proposal::getValor).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal precoMax = propostas.stream().map(Proposal::getValor).max(Comparator.naturalOrder()).orElse(null);

        Integer tempoRespostaMin = null;
        if (!propostas.isEmpty()) {
            long mediaMinutos = Math.round(propostas.stream()
                    .mapToLong(pr -> Duration.between(
                            pr.getServiceRequest().getCreatedAt(), pr.getCreatedAt()).toMinutes())
                    .average()
                    .orElse(0));
            // O front trata 0 como "sem dado" (`profile.tempoRespostaMin ? ... : '—'`) — um
            // prestador que responde em segundos não pode desaparecer da própria estatística.
            tempoRespostaMin = (int) Math.max(1, mediaMinutos);
        }

        return new ProviderPublicDto(
                p.getUser().getId(),
                p.getUser().getNome(),
                p.getCategoria(),
                p.getBio(),
                p.getNotaMedia(),
                reviews.size(),
                (int) proposalRepository.countByPrestadorIdAndStatus(userId, ProposalStatus.ACEITA),
                p.getStatusVerificacao().name(),
                avaliacoes,
                tempoRespostaMin,
                precoMin,
                precoMax);
    }
}
