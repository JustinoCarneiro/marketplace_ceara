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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ReviewServiceTest {

    @Mock ReviewRepository          reviewRepository;
    @Mock ServiceRequestRepository  srRepository;
    @Mock ProposalRepository        proposalRepository;
    @Mock ProviderProfileRepository providerProfileRepository;

    ReviewService service;

    private static final UUID SR_ID        = UUID.randomUUID();
    private static final UUID CLIENTE_ID   = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final long PRAZO_DIAS   = 14;

    @BeforeEach
    void setUp() {
        service = new ReviewService(
                reviewRepository, srRepository, proposalRepository, providerProfileRepository,
                PRAZO_DIAS);
    }

    @Test
    void avaliar_comoCliente_cria_CLIENTE_AVALIA_PRESTADOR() {
        stubSrConcluido();
        stubSemReview();
        stubPrestadorDaProposta();
        stubSave();

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
        stubSave();

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

    // ----- double-blind (reveal simultâneo) -----

    @Test
    void avaliar_semContraparte_ficaOculta_eNaoMexeNaNotaMedia() {
        stubSrConcluido();
        stubSemReview();
        stubPrestadorDaProposta();
        stubSave();
        when(reviewRepository.findByServiceRequestIdAndTipo(SR_ID, ReviewType.PRESTADOR_AVALIA_CLIENTE))
                .thenReturn(Optional.empty());

        ReviewDto dto = service.avaliar(SR_ID, CLIENTE_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR,
                new AvaliarRequest(5, "Ótimo!"));

        assertThat(dto.revelada()).isFalse();
        assertThat(dto.prazoRevelacao())
                .isCloseTo(Instant.now().plus(Duration.ofDays(PRAZO_DIAS)),
                        within(1, java.time.temporal.ChronoUnit.MINUTES));
        // a nota oculta não pode entrar na média — senão a média denunciaria a nota
        verify(providerProfileRepository, never()).save(any());
    }

    @Test
    void avaliar_contraparteJaAvaliou_revelaAsDuas_eAtualizaNotaMedia() {
        stubSrConcluido();
        stubSemReview();
        when(srRepository.findClienteIdBySrId(SR_ID)).thenReturn(Optional.of(CLIENTE_ID));
        stubSave();

        // o cliente já tinha avaliado e ficou oculta aguardando o prestador
        Review doCliente = new Review(SR_ID, CLIENTE_ID, PRESTADOR_ID,
                ReviewType.CLIENTE_AVALIA_PRESTADOR, 5, "Excelente!");
        when(reviewRepository.findByServiceRequestIdAndTipo(SR_ID, ReviewType.CLIENTE_AVALIA_PRESTADOR))
                .thenReturn(Optional.of(doCliente));
        stubMediaEPerfil(5.0);

        // agora o prestador avalia — é essa avaliação que dispara a revelação de ambas
        ReviewDto dto = service.avaliar(SR_ID, PRESTADOR_ID, ReviewType.PRESTADOR_AVALIA_CLIENTE,
                new AvaliarRequest(4, "Cliente pontual."));

        assertThat(dto.revelada()).isTrue();
        assertThat(doCliente.isRevelada()).isTrue();
        verify(providerProfileRepository).save(any(ProviderProfile.class));
    }

    @Test
    void revelar_porPrazoVencido_publicaEAtualizaNotaMedia() {
        Review sozinha = new Review(SR_ID, CLIENTE_ID, PRESTADOR_ID,
                ReviewType.CLIENTE_AVALIA_PRESTADOR, 5, "Excelente!");
        when(reviewRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        stubMediaEPerfil(5.0);

        service.revelar(List.of(sozinha));

        assertThat(sozinha.isRevelada()).isTrue();
        assertThat(sozinha.getReveladaEm()).isNotNull();
        verify(providerProfileRepository).save(any(ProviderProfile.class));
    }

    @Test
    void revelar_jaRevelada_naoRecalculaNemRegravaAData() {
        Review jaRevelada = new Review(SR_ID, CLIENTE_ID, PRESTADOR_ID,
                ReviewType.CLIENTE_AVALIA_PRESTADOR, 5, "Excelente!");
        jaRevelada.revelar();
        Instant primeiraRevelacao = jaRevelada.getReveladaEm();

        service.revelar(List.of(jaRevelada));

        assertThat(jaRevelada.getReveladaEm()).isEqualTo(primeiraRevelacao);
        verify(reviewRepository, never()).saveAll(any());
        verify(providerProfileRepository, never()).save(any());
    }

    @Test
    void buscarVencidas_consultaPeloPrazoConfigurado() {
        Instant agora = Instant.parse("2026-08-07T12:00:00Z");
        when(reviewRepository.findByReveladaFalseAndCriadoEmBefore(any())).thenReturn(List.of());

        service.buscarVencidas(agora);

        verify(reviewRepository).findByReveladaFalseAndCriadoEmBefore(
                agora.minus(Duration.ofDays(PRAZO_DIAS)));
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

    private void stubSave() {
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void stubPrestadorDaProposta() {
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta(PRESTADOR_ID)));
    }

    private void stubMediaEPerfil(double media) {
        when(reviewRepository.calcularMediaNota(eq(PRESTADOR_ID), eq(ReviewType.CLIENTE_AVALIA_PRESTADOR)))
                .thenReturn(media);
        when(providerProfileRepository.findByUserId(PRESTADOR_ID))
                .thenReturn(Optional.of(mock(ProviderProfile.class)));
        when(providerProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Proposal proposta(UUID prestadorId) {
        var sr = sr(ServiceRequestStatus.ACEITO);
        return new Proposal(sr, prestadorId, BigDecimal.valueOf(200), 3, ProposalStatus.ACEITA);
    }
}
