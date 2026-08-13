package com.onda.marketplace.provider;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProviderPublicServiceTest {

    @Mock ProviderProfileRepository profileRepository;
    @Mock ReviewRepository          reviewRepository;
    @Mock UserRepository            userRepository;
    @Mock ProposalRepository        proposalRepository;

    ProviderPublicService service;

    @BeforeEach
    void setUp() {
        service = new ProviderPublicService(profileRepository, reviewRepository, userRepository, proposalRepository);
    }

    @Test
    void buscarPorUserId_prestadorComAvaliacoes_retornaPerfilCompleto() {
        UUID userId = UUID.randomUUID();
        UUID avaliadorId = UUID.randomUUID();

        var prestador = User.builder().nome("Zé Elétrica").email("ze@test.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build();
        setId(prestador, userId);
        var perfil = new ProviderProfile(prestador, "Elétrica", "cpf-cifrado");
        perfil.setNotaMedia(BigDecimal.valueOf(4.8));

        var review = mock(Review.class);
        when(review.getAvaliadorId()).thenReturn(avaliadorId);
        when(review.getNota()).thenReturn(5);
        when(review.getComentario()).thenReturn("Excelente serviço!");

        var cliente = User.builder().nome("Maria").email("maria@test.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(perfil));
        when(reviewRepository.findByAvaliadoIdAndTipoAndReveladaTrueOrderByCriadoEmDesc(
                userId, ReviewType.CLIENTE_AVALIA_PRESTADOR))
                .thenReturn(List.of(review));
        when(userRepository.findById(avaliadorId)).thenReturn(Optional.of(cliente));
        when(proposalRepository.countByPrestadorIdAndStatus(userId, ProposalStatus.ACEITA)).thenReturn(3L);
        when(proposalRepository.findByPrestadorId(userId)).thenReturn(List.of());

        ProviderPublicDto dto = service.buscarPorUserId(userId);

        assertThat(dto.nome()).isEqualTo("Zé Elétrica");
        assertThat(dto.notaMedia()).isEqualByComparingTo("4.8");
        assertThat(dto.totalAvaliacoes()).isEqualTo(1);
        assertThat(dto.totalServicos()).isEqualTo(3);
        assertThat(dto.avaliacoes()).hasSize(1);
        assertThat(dto.avaliacoes().get(0).autorNome()).isEqualTo("Maria");
        assertThat(dto.avaliacoes().get(0).comentario()).isEqualTo("Excelente serviço!");
        // Sem proposta nenhuma ainda — nada pra calcular, os 3 campos ficam nulos (front exibe "—").
        assertThat(dto.tempoRespostaMin()).isNull();
        assertThat(dto.precoMin()).isNull();
        assertThat(dto.precoMax()).isNull();
    }

    @Test
    void buscarPorUserId_comPropostas_calculaFaixaDePrecoETempoDeResposta() {
        UUID userId = UUID.randomUUID();
        var prestador = User.builder().nome("Zé Elétrica").email("ze@test.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build();
        setId(prestador, userId);
        var perfil = new ProviderProfile(prestador, "Elétrica", "cpf-cifrado");

        var sr1 = new ServiceRequest();
        setCreatedAt(sr1, Instant.parse("2026-08-01T10:00:00Z"));
        var p1 = new Proposal(sr1, userId, new BigDecimal("150.00"), 2, ProposalStatus.ATIVA);
        setCreatedAt(p1, Instant.parse("2026-08-01T10:10:00Z")); // 10 min de resposta

        var sr2 = new ServiceRequest();
        setCreatedAt(sr2, Instant.parse("2026-08-02T09:00:00Z"));
        var p2 = new Proposal(sr2, userId, new BigDecimal("300.00"), 1, ProposalStatus.ACEITA);
        setCreatedAt(p2, Instant.parse("2026-08-02T09:20:00Z")); // 20 min de resposta

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(perfil));
        when(reviewRepository.findByAvaliadoIdAndTipoAndReveladaTrueOrderByCriadoEmDesc(
                userId, ReviewType.CLIENTE_AVALIA_PRESTADOR)).thenReturn(List.of());
        when(proposalRepository.countByPrestadorIdAndStatus(userId, ProposalStatus.ACEITA)).thenReturn(1L);
        when(proposalRepository.findByPrestadorId(userId)).thenReturn(List.of(p1, p2));

        ProviderPublicDto dto = service.buscarPorUserId(userId);

        assertThat(dto.precoMin()).isEqualByComparingTo("150.00");
        assertThat(dto.precoMax()).isEqualByComparingTo("300.00");
        assertThat(dto.tempoRespostaMin()).isEqualTo(15); // média de 10 e 20 min
    }

    @Test
    void buscarPorUserId_prestadorInexistente_lancaBusinessException() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorUserId(userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não encontrado");
    }

    private static void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setCreatedAt(Object entity, Instant value) {
        try {
            var field = entity.getClass().getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
