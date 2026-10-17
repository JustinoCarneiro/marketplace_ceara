package com.onda.marketplace.provider;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.proposal.ProposalTiming;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
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
    private final long                      toleranciaPontualidadeMin;

    public ProviderPublicService(ProviderProfileRepository profileRepository,
                                 ReviewRepository reviewRepository,
                                 UserRepository userRepository,
                                 ProposalRepository proposalRepository,
                                 @Value("${marketplace.pontualidade.tolerancia-minutos:30}") long toleranciaPontualidadeMin) {
        this.profileRepository  = profileRepository;
        this.reviewRepository   = reviewRepository;
        this.userRepository     = userRepository;
        this.proposalRepository = proposalRepository;
        this.toleranciaPontualidadeMin = toleranciaPontualidadeMin;
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

        var ids = new java.util.HashSet<UUID>(); ids.add(userId);
        reviews.forEach(r -> ids.add(r.getAvaliadorId()));
        var nomes = userRepository.findNamesByIdIn(ids).stream().collect(java.util.stream.Collectors.toMap(
                com.onda.marketplace.auth.UserName::id, com.onda.marketplace.auth.UserName::nome));

        List<ProviderPublicDto.Avaliacao> avaliacoes = reviews.stream()
                .map(r -> new ProviderPublicDto.Avaliacao(
                        r.getId(),
                        nomes.getOrDefault(r.getAvaliadorId(), "Cliente"),
                        r.getNota(),
                        r.getComentario()))
                .toList();

        List<ProposalTiming> propostas = proposalRepository.findTimingsByPrestadorId(userId);
        BigDecimal precoMin = propostas.stream().map(ProposalTiming::valor).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal precoMax = propostas.stream().map(ProposalTiming::valor).max(Comparator.naturalOrder()).orElse(null);

        Integer tempoRespostaMin = null;
        if (!propostas.isEmpty()) {
            long mediaMinutos = Math.round(propostas.stream()
                    .mapToLong(pr -> Duration.between(
                            pr.requestCreatedAt(), pr.createdAt()).toMinutes())
                    .average()
                    .orElse(0));
            // O front trata 0 como "sem dado" (`profile.tempoRespostaMin ? ... : '—'`) — um
            // prestador que responde em segundos não pode desaparecer da própria estatística.
            tempoRespostaMin = (int) Math.max(1, mediaMinutos);
        }

        // Pontualidade (US03): só entram atendimentos que de fato começaram (iniciadoEm) com
        // uma proposta aceita que tinha horário combinado (horarioProposto) — propostas sem
        // esse dado (anteriores à feature) ou pedidos nunca iniciados não contam pra nenhum
        // dos dois lados, não puxam a média nem pra cima nem pra baixo.
        List<ProposalTiming> atendimentosIniciados = propostas.stream()
                .filter(pr -> pr.status() == ProposalStatus.ACEITA)
                .filter(pr -> pr.horarioProposto() != null)
                .filter(pr -> pr.iniciadoEm() != null)
                .toList();

        Integer pontualidadePct = null;
        if (!atendimentosIniciados.isEmpty()) {
            long pontuais = atendimentosIniciados.stream()
                    .filter(pr -> !pr.iniciadoEm()
                            .isAfter(pr.horarioProposto().plusSeconds(toleranciaPontualidadeMin * 60)))
                    .count();
            pontualidadePct = (int) Math.round(100.0 * pontuais / atendimentosIniciados.size());
        }

        return new ProviderPublicDto(
                p.getUserId(),
                nomes.getOrDefault(p.getUserId(), "Prestador"),
                p.getCategoria(),
                p.getBio(),
                p.getNotaMedia(),
                reviews.size(),
                (int) proposalRepository.countByPrestadorIdAndStatus(userId, ProposalStatus.ACEITA),
                p.getStatusVerificacao().name(),
                avaliacoes,
                tempoRespostaMin,
                precoMin,
                precoMax,
                pontualidadePct);
    }
}
