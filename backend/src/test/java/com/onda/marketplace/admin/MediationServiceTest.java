package com.onda.marketplace.admin;
import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
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
class MediationServiceTest {

    @Mock ServiceRequestRepository    srRepository;
    @Mock TransactionRepository        transactionRepository;
    @Mock OutboxEventRepository        outboxRepository;
    @Mock DisputeResolutionRepository  resolutionRepository;

    MediationService service;

    private static final UUID SR_ID    = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MediationService(
                srRepository, transactionRepository, outboxRepository,
                resolutionRepository);
    }

    @Test
    void resolver_concluir_moveParaConcluido_e_criaOutboxReleased() {
        var sr = sr(ServiceRequestStatus.EM_DISPUTA);
        var tx = transaction(TransactionStatus.RETIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolver(SR_ID, ADMIN_ID,
                new ResolveDisputeRequest(MediationDecision.CONCLUIR, "serviço entregue conforme"));

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.CONCLUIDO);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_RELEASED");
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDENTE);
    }

    @Test
    void resolver_reembolsar_moveParaCancelado_e_criaOutboxRefunded() {
        var sr = sr(ServiceRequestStatus.EM_DISPUTA);
        var tx = transaction(TransactionStatus.RETIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolver(SR_ID, ADMIN_ID,
                new ResolveDisputeRequest(MediationDecision.REEMBOLSAR, "cliente tem razão"));

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.CANCELADO);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    void resolver_registraDecisaoDeAuditoria() {
        var sr = sr(ServiceRequestStatus.EM_DISPUTA);
        var tx = transaction(TransactionStatus.RETIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(resolutionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolver(SR_ID, ADMIN_ID,
                new ResolveDisputeRequest(MediationDecision.CONCLUIR, "ok"));

        ArgumentCaptor<DisputeResolution> captor = ArgumentCaptor.forClass(DisputeResolution.class);
        verify(resolutionRepository).save(captor.capture());
        assertThat(captor.getValue().getAdminId()).isEqualTo(ADMIN_ID);
        assertThat(captor.getValue().getDecisao()).isEqualTo(MediationDecision.CONCLUIR);
    }

    @Test
    void resolver_naoEstaEmDisputa_lancaException() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() -> service.resolver(SR_ID, ADMIN_ID,
                new ResolveDisputeRequest(MediationDecision.CONCLUIR, "x")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "NOT_IN_DISPUTE");
    }

    // helpers
    private ServiceRequest sr(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        return sr;
    }

    private Transaction transaction(TransactionStatus status) {
        var tx = new Transaction(SR_ID, BigDecimal.valueOf(200), BigDecimal.valueOf(30),
                BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-disp");
        if (status == TransactionStatus.RETIDO) tx.reter();
        return tx;
    }
}
