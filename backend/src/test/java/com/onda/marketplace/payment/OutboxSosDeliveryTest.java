package com.onda.marketplace.payment;

import com.onda.marketplace.notification.NotificationDeliveryException;
import com.onda.marketplace.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Entrega do alerta de SOS pelo Outbox (US21/US30).
 *
 * <p>Antes, {@code SOS_TRIGGERED} só produzia um {@code log.warn} e era marcado como
 * PROCESSADO: o mecanismo durável existia e não entregava nada. E se a entrega falhasse,
 * ninguém ficava sabendo — é o cenário que estes testes travam.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class OutboxSosDeliveryTest {

    @Mock OutboxEventRepository outboxRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock GatewayService        gatewayService;
    @Mock NotificationService   notificationService;

    OutboxProcessor processor;
    @Mock OutboxEventWriter eventWriter;

    private static final UUID SOS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        processor = new OutboxProcessor(transactionRepository,
                gatewayService, notificationService, eventWriter);
    }

    private OutboxEvent eventoSos() {
        return new OutboxEvent("sos_alert", SOS_ID, "SOS_TRIGGERED", "{\"userId\":\"x\"}");
    }

    @Test
    void sosTriggered_entregaOAlertaEMarcaProcessado() {
        OutboxEvent evento = eventoSos();
        when(eventWriter.reservarProximo())
                .thenReturn(evento, (OutboxEvent) null);

        processor.processOutbox();

        verify(notificationService).entregar("SOS", SOS_ID);
        verify(eventWriter).marcarProcessado(evento);
    }

    @Test
    void sosNaoEntregue_viraFALHA_paraAparecerNaReconciliacao() {
        OutboxEvent evento = eventoSos();
        when(eventWriter.reservarProximo())
                .thenReturn(evento, (OutboxEvent) null);
        doThrow(new NotificationDeliveryException("SMTP fora do ar", new RuntimeException()))
                .when(notificationService).entregar(eq("SOS"), any());

        processor.processOutbox();

        // Não pode virar PROCESSADO: o admin não foi avisado. FALHA deixa a pendência
        // visível no painel (US27) e reprocessável.
        verify(eventWriter).salvarFalha(evento);


    }

    @Test
    void sosNaoEntregue_naoDerrubaOProcessamentoDosDemaisEventos() {
        OutboxEvent sos   = eventoSos();
        OutboxEvent outro = new OutboxEvent("qualquer", UUID.randomUUID(), "DESCONHECIDO", "{}");
        when(eventWriter.reservarProximo())
                .thenReturn(sos, outro, null);
        doThrow(new NotificationDeliveryException("SMTP fora do ar", new RuntimeException()))
                .when(notificationService).entregar(eq("SOS"), any());

        processor.processOutbox();

        verify(eventWriter).salvarFalha(sos);
        verify(eventWriter).marcarProcessado(outro);
    }
}
