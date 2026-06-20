package com.onda.marketplace.execution;

import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceExecutionServiceTest {

    @Mock ServiceRequestRepository srRepository;
    @Mock ProposalRepository       proposalRepository;
    @Mock TransactionRepository    transactionRepository;
    @Mock OutboxEventRepository    outboxRepository;

    ServiceExecutionService service;

    private static final UUID SR_ID       = UUID.randomUUID();
    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final UUID CLIENTE_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ServiceExecutionService(
                srRepository, proposalRepository, transactionRepository, outboxRepository);
    }

    // ----- start -----

    @Test
    void start_aceitoEPrestadorCorreto_moveParaEmAndamento() {
        var sr = sr(ServiceRequestStatus.ACEITO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta(PRESTADOR_ID)));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.start(SR_ID, PRESTADOR_ID);

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.EM_ANDAMENTO);
        verify(srRepository).save(sr);
    }

    @Test
    void start_statusNaoAceito_lancaException() {
        var sr = sr(ServiceRequestStatus.PENDENTE);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() -> service.start(SR_ID, PRESTADOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    @Test
    void start_prestadorErrado_lancaException() {
        var sr = sr(ServiceRequestStatus.ACEITO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta(UUID.randomUUID()))); // outro prestador

        assertThatThrownBy(() -> service.start(SR_ID, PRESTADOR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UNAUTHORIZED_PROVIDER");
    }

    // ----- confirmCompletion -----

    @Test
    void confirmCompletion_emAndamento_moveParaConcluido_e_criaOutbox() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        var tx = transaction(TransactionStatus.RETIDO);
        when(srRepository.findByIdAndCliente_Id(SR_ID, CLIENTE_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmCompletion(SR_ID, CLIENTE_ID);

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.CONCLUIDO);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_RELEASED");
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDENTE);
    }

    @Test
    void confirmCompletion_statusInvalido_lancaException() {
        var sr = sr(ServiceRequestStatus.ACEITO);
        when(srRepository.findByIdAndCliente_Id(SR_ID, CLIENTE_ID)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() -> service.confirmCompletion(SR_ID, CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    // ----- openDispute -----

    @Test
    void openDispute_emAndamento_moveParaEmDisputa() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.openDispute(SR_ID);

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.EM_DISPUTA);
        verify(srRepository).save(sr);
    }

    @Test
    void openDispute_statusInvalido_lancaException() {
        var sr = sr(ServiceRequestStatus.ACEITO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() -> service.openDispute(SR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    // ----- cancel -----

    @Test
    void cancel_aceito_moveParaCancelado_e_criaOutboxReembolso() {
        var sr = sr(ServiceRequestStatus.ACEITO);
        var tx = transaction(TransactionStatus.RETIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancel(SR_ID);

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.CANCELADO);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    void cancel_emAndamento_semTransacao_moveParaCancelado_semOutbox() {
        var sr = sr(ServiceRequestStatus.EM_ANDAMENTO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.empty());
        when(srRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.cancel(SR_ID);

        assertThat(sr.getStatus()).isEqualTo(ServiceRequestStatus.CANCELADO);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void cancel_statusInvalido_lancaException() {
        var sr = sr(ServiceRequestStatus.CONCLUIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));

        assertThatThrownBy(() -> service.cancel(SR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_STATE_TRANSITION");
    }

    // helpers
    private ServiceRequest sr(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        return sr;
    }

    private Proposal proposta(UUID prestadorId) {
        var sr = sr(ServiceRequestStatus.ACEITO);
        return new Proposal(sr, prestadorId, BigDecimal.valueOf(200), 3, ProposalStatus.ACEITA);
    }

    private Transaction transaction(TransactionStatus status) {
        var tx = new Transaction(SR_ID, BigDecimal.valueOf(200), BigDecimal.valueOf(30),
                BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-exec");
        if (status == TransactionStatus.RETIDO) tx.reter();
        return tx;
    }
}
