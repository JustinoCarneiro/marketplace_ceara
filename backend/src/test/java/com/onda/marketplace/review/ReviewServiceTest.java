package com.onda.marketplace.review;

import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository          reviewRepository;
    @Mock ServiceRequestRepository  srRepository;
    @Mock ProposalRepository        proposalRepository;
    @Mock ProviderProfileRepository providerProfileRepository;

    ReviewService service;

    private static final UUID SR_ID        = UUID.randomUUID();
    private static final UUID CLIENTE_ID   = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReviewService(
                reviewRepository, srRepository, proposalRepository, providerProfileRepository);
    }

    @Test
    void avaliar_comoCliente_cria_CLIENTE_AVALIA_PRESTADOR() {
        stubSrConcluido();
        stubSemReview();
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta(PRESTADOR_ID)));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReviewDto dto = service.avaliar(SR_ID, CLIENTE_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR,
                new AvaliarRequest(5, "Excelente!"));

        assertThat(dto.nota()).isEqualTo(5);
        assertThat(dto.tipo()).isEqualTo("CLIENTE_AVALIA_PRESTADOR");
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void avaliar_comoPrestador_cria_PRESTADOR_AVALIA_CLIENTE() {
        stubSrConcluido();
        stubSemReview();
        when(srRepository.findClienteIdBySrId(SR_ID)).thenReturn(Optional.of(CLIENTE_ID));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReviewDto dto = service.avaliar(SR_ID, PRESTADOR_ID, ReviewType.PRESTADOR_AVALIA_CLIENTE,
                new AvaliarRequest(4, "Cliente pontual."));

        assertThat(dto.nota()).isEqualTo(4);
        assertThat(dto.tipo()).isEqualTo("PRESTADOR_AVALIA_CLIENTE");
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void avaliar_srNaoConcluido_lancaException() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() ->
                service.avaliar(SR_ID, CLIENTE_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR,
                        new AvaliarRequest(5, "ok")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_NOT_ALLOWED");
    }

    @Test
    void avaliar_reviewDuplicado_lancaException() {
        stubSrConcluido();
        when(reviewRepository.existsByServiceRequestIdAndTipo(SR_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR))
                .thenReturn(true);

        assertThatThrownBy(() ->
                service.avaliar(SR_ID, CLIENTE_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR,
                        new AvaliarRequest(5, "ok")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_ALREADY_EXISTS");
    }

    @Test
    void avaliar_clienteAvalia_atualizaNotaMediaDoPrestador() {
        stubSrConcluido();
        stubSemReview();
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta(PRESTADOR_ID)));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reviewRepository.calcularMediaNota(eq(PRESTADOR_ID), eq(ReviewType.CLIENTE_AVALIA_PRESTADOR)))
                .thenReturn(4.5);
        var profile = mock(ProviderProfile.class);
        when(providerProfileRepository.findByUserId(PRESTADOR_ID)).thenReturn(Optional.of(profile));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.avaliar(SR_ID, CLIENTE_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR,
                new AvaliarRequest(5, "Ótimo!"));

        verify(profile).setNotaMedia(BigDecimal.valueOf(4.5));
        verify(providerProfileRepository).save(profile);
    }

    // helpers
    private ServiceRequest sr(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        return sr;
    }

    private void stubSrConcluido() {
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr(ServiceRequestStatus.CONCLUIDO)));
    }

    private void stubSemReview() {
        when(reviewRepository.existsByServiceRequestIdAndTipo(any(), any())).thenReturn(false);
    }

    private Proposal proposta(UUID prestadorId) {
        var sr = sr(ServiceRequestStatus.ACEITO);
        return new Proposal(sr, prestadorId, BigDecimal.valueOf(200), 3, ProposalStatus.ACEITA);
    }
}
