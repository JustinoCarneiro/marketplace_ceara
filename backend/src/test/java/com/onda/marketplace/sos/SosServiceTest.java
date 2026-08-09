package com.onda.marketplace.sos;

import com.onda.marketplace.notification.NotificationService;
import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SosServiceTest {

    @Mock SosAlertRepository       alertRepository;
    @Mock OutboxEventRepository    outboxRepository;
    @Mock NotificationService      notificationService;
    @Mock ServiceRequestRepository srRepository;

    SosService service;

    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID ALERT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SosService(alertRepository, outboxRepository, notificationService, srRepository);
    }

    @Test
    void acionarSos_cria_alerta_e_outboxSosTriggered_e_registraNoPainel() {
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notificationService.registrarAlerta(any(), any())).thenReturn(null);

        SosAlertDto dto = service.acionarSos(USER_ID,
                new AcionarSosRequest(null, null, null));

        assertThat(dto.status()).isEqualTo("ATIVO");
        verify(alertRepository).save(any(SosAlert.class));

        // O evento nasce PENDENTE: é ele que garante a entrega do aviso ao admin.
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("SOS_TRIGGERED");
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDENTE);

        // Aqui só registra no painel. Entregar é responsabilidade do OutboxProcessor —
        // enviar daqui seria best-effort dentro da transação, que é o que fazia o aviso
        // sumir quando o SMTP falhava.
        verify(notificationService).registrarAlerta(eq("SOS"), any());
        verify(notificationService, never()).entregar(any(), any());
    }

    @Test
    void acionarSos_comSrId_salvaVinculo() {
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID srId = UUID.randomUUID();
        when(srRepository.isParticipante(srId, USER_ID)).thenReturn(true);

        service.acionarSos(USER_ID, new AcionarSosRequest(srId,
                BigDecimal.valueOf(-3.71), BigDecimal.valueOf(-38.54)));

        ArgumentCaptor<SosAlert> captor = ArgumentCaptor.forClass(SosAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getServiceRequestId()).isEqualTo(srId);
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo(BigDecimal.valueOf(-3.71));
    }

    @Test
    void acionarSos_pedidoAlheio_registraAlertaSemOVinculo() {
        // Botão de pânico: o acionamento NUNCA é recusado (quem está em risco tem que
        // conseguir acionar). Mas um vínculo não conferido apontaria o admin para o pedido
        // de outra pessoa no meio da emergência — então o alerta é salvo sem ele.
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UUID srAlheio = UUID.randomUUID();
        when(srRepository.isParticipante(srAlheio, USER_ID)).thenReturn(false);

        SosAlertDto dto = service.acionarSos(USER_ID, new AcionarSosRequest(srAlheio,
                BigDecimal.valueOf(-3.71), BigDecimal.valueOf(-38.54)));

        assertThat(dto.status()).isEqualTo("ATIVO");   // acionou, não foi recusado
        ArgumentCaptor<SosAlert> captor = ArgumentCaptor.forClass(SosAlert.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getServiceRequestId()).isNull();
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo(BigDecimal.valueOf(-3.71));
    }

    @Test
    void resolver_alertaAtivo_moveParaResolvido() {
        var alert = new SosAlert(USER_ID, null, null, null);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolver(ALERT_ID);

        assertThat(alert.getStatus()).isEqualTo(SosAlertStatus.RESOLVIDO);
        verify(alertRepository).save(alert);
    }

    @Test
    void resolver_alertaNaoEncontrado_lancaException() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver(ALERT_ID))
                .isInstanceOf(com.onda.marketplace.shared.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SOS_NOT_FOUND");
    }
}
