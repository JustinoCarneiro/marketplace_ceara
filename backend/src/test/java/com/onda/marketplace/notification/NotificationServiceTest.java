package com.onda.marketplace.notification;

import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class NotificationServiceTest {

    @Mock AdminNotificationRepository repository;
    @Mock EmailSender                  emailSender;
    @Mock PushSender                   pushSender;

    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, emailSender, pushSender);
    }

    // ----- criarAlerta -----

    @Test
    void criarAlerta_SOS_persisteEDispararaCanaisExternos() {
        UUID refId = UUID.randomUUID();
        AdminNotification notif = new AdminNotification("SOS", refId);
        when(repository.save(any())).thenReturn(notif);

        AdminNotificationDto dto = service.criarAlerta("SOS", refId);

        assertThat(dto.tipo()).isEqualTo("SOS");
        assertThat(dto.refId()).isEqualTo(refId);
        assertThat(dto.lida()).isFalse();

        verify(emailSender).enviar("SOS", refId);
        verify(pushSender).enviar("SOS", refId);
    }

    @Test
    void criarAlerta_DISPUTA_persisteComTipoCorreto() {
        UUID srId = UUID.randomUUID();
        AdminNotification notif = new AdminNotification("DISPUTA", srId);
        when(repository.save(any())).thenReturn(notif);

        ArgumentCaptor<AdminNotification> captor = ArgumentCaptor.forClass(AdminNotification.class);

        service.criarAlerta("DISPUTA", srId);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo("DISPUTA");
        assertThat(captor.getValue().getRefId()).isEqualTo(srId);
    }

    @Test
    void criarAlerta_VERIFICACAO_disparaEmail() {
        UUID providerId = UUID.randomUUID();
        AdminNotification notif = new AdminNotification("VERIFICACAO", providerId);
        when(repository.save(any())).thenReturn(notif);

        service.criarAlerta("VERIFICACAO", providerId);

        verify(emailSender).enviar(eq("VERIFICACAO"), eq(providerId));
    }

    // ----- listar -----

    @Test
    void listar_semFiltro_retornaTodas() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.findAllByOrderByCriadoEmDesc()).thenReturn(List.of(
                new AdminNotification("SOS", id1),
                new AdminNotification("DISPUTA", id2)));

        List<AdminNotificationDto> lista = service.listar(null);

        assertThat(lista).hasSize(2);
        assertThat(lista).extracting(AdminNotificationDto::tipo)
                .containsExactlyInAnyOrder("SOS", "DISPUTA");
    }

    @Test
    void listar_comFiltroNaoLida_delegaAoRepositorioComFalse() {
        when(repository.findByLidaOrderByCriadoEmDesc(false)).thenReturn(List.of());

        service.listar(false);

        verify(repository).findByLidaOrderByCriadoEmDesc(false);
        verify(repository, never()).findAllByOrderByCriadoEmDesc();
    }

    // ----- marcarLida -----

    @Test
    void marcarLida_entidadeExistente_setaFlagLida() {
        UUID id = UUID.randomUUID();
        AdminNotification notif = new AdminNotification("SOS", UUID.randomUUID());
        when(repository.findById(id)).thenReturn(Optional.of(notif));

        service.marcarLida(id);

        assertThat(notif.isLida()).isTrue();
        verify(repository).save(notif);
    }

    @Test
    void marcarLida_notificacaoInexistente_lancaBusinessException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.marcarLida(id))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOTIFICATION_NOT_FOUND");
    }

    // ----- marcarTodasLidas -----

    @Test
    void marcarTodasLidas_delegaAoRepositorio_eRetornaAfetadas() {
        when(repository.marcarTodasLidas()).thenReturn(3);

        int afetadas = service.marcarTodasLidas();

        assertThat(afetadas).isEqualTo(3);
        verify(repository).marcarTodasLidas();
    }
}
