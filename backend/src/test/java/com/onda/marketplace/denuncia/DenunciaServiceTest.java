package com.onda.marketplace.denuncia;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.review.Review;
import com.onda.marketplace.review.ReviewRepository;
import com.onda.marketplace.review.ReviewType;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DenunciaServiceTest {

    @Mock DenunciaRepository  denunciaRepository;
    @Mock UserRepository      userRepository;
    @Mock ReviewRepository    reviewRepository;
    @Mock NotificationService notificationService;

    DenunciaService service;

    private static final UUID DENUNCIANTE_ID = UUID.randomUUID();
    private static final UUID PRESTADOR_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DenunciaService(denunciaRepository, userRepository, reviewRepository, notificationService);
    }

    @Test
    void criar_prestadorExistente_salvaEAlertaAdmin() {
        when(userRepository.existsById(PRESTADOR_ID)).thenReturn(true);
        when(denunciaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DenunciaDto dto = service.criar(DENUNCIANTE_ID,
                new CreateDenunciaRequest(TipoDenuncia.PRESTADOR, PRESTADOR_ID, "Perfil falso", "detalhes aqui"));

        assertThat(dto.tipo()).isEqualTo("PRESTADOR");
        assertThat(dto.status()).isEqualTo("ABERTA");
        verify(notificationService).criarAlerta(eq("DENUNCIA"), any());
    }

    @Test
    void criar_alvoInexistente_lancaBusinessException() {
        when(userRepository.existsById(PRESTADOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.criar(DENUNCIANTE_ID,
                new CreateDenunciaRequest(TipoDenuncia.PRESTADOR, PRESTADOR_ID, "Perfil falso", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ALVO_NAO_ENCONTRADO");
        verify(denunciaRepository, never()).save(any());
        verify(notificationService, never()).criarAlerta(any(), any());
    }

    @Test
    void criar_avaliacaoInexistente_lancaBusinessException() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.existsById(reviewId)).thenReturn(false);

        assertThatThrownBy(() -> service.criar(DENUNCIANTE_ID,
                new CreateDenunciaRequest(TipoDenuncia.AVALIACAO, reviewId, "Avaliação falsa", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ALVO_NAO_ENCONTRADO");
    }

    @Test
    void listarAbertas_resolveNomesParaOModerador() {
        var denuncia = new Denuncia(TipoDenuncia.PRESTADOR, PRESTADOR_ID, DENUNCIANTE_ID, "Perfil falso", "x");
        when(denunciaRepository.findByStatusOrderByCriadoEmDesc(StatusDenuncia.ABERTA))
                .thenReturn(List.of(denuncia));
        when(userRepository.findById(PRESTADOR_ID)).thenReturn(Optional.of(
                User.builder().nome("Zé Elétrica").email("ze@test.com").senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build()));
        when(userRepository.findById(DENUNCIANTE_ID)).thenReturn(Optional.of(
                User.builder().nome("Maria").email("maria@test.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build()));

        List<DenunciaAdminDto> lista = service.listarAbertas();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).alvoLabel()).isEqualTo("Zé Elétrica");
        assertThat(lista.get(0).denuncianteNome()).isEqualTo("Maria");
    }

    @Test
    void listarAbertas_avaliacaoDenunciada_resumoTraNotaEComentario() {
        UUID reviewId = UUID.randomUUID();
        var denuncia = new Denuncia(TipoDenuncia.AVALIACAO, reviewId, DENUNCIANTE_ID, "Avaliação falsa", null);
        when(denunciaRepository.findByStatusOrderByCriadoEmDesc(StatusDenuncia.ABERTA))
                .thenReturn(List.of(denuncia));
        when(userRepository.findById(DENUNCIANTE_ID)).thenReturn(Optional.of(
                User.builder().nome("Zé Elétrica").email("ze@test.com").senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build()));
        Review review = new Review(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ReviewType.CLIENTE_AVALIA_PRESTADOR, 1, "Trabalho péssimo, nunca apareceu.");
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        List<DenunciaAdminDto> lista = service.listarAbertas();

        assertThat(lista.get(0).alvoLabel()).contains("Nota 1").contains("Trabalho péssimo");
    }

    @Test
    void resolver_denunciaExistente_marcaResolvidaComAdminId() {
        var denuncia = new Denuncia(TipoDenuncia.PRESTADOR, PRESTADOR_ID, DENUNCIANTE_ID, "Perfil falso", null);
        UUID adminId = UUID.randomUUID();
        when(denunciaRepository.findById(any())).thenReturn(Optional.of(denuncia));
        when(denunciaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolver(UUID.randomUUID(), adminId);

        assertThat(denuncia.getStatus()).isEqualTo(StatusDenuncia.RESOLVIDA);
        assertThat(denuncia.getResolvidoPorId()).isEqualTo(adminId);
        assertThat(denuncia.getResolvidoEm()).isNotNull();
    }

    @Test
    void resolver_denunciaInexistente_lancaBusinessException() {
        when(denunciaRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DENUNCIA_NOT_FOUND");
    }
}
