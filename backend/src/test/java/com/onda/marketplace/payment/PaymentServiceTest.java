package com.onda.marketplace.payment;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
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
@SuppressWarnings("null")
class PaymentServiceTest {

    @Mock TransactionRepository    transactionRepository;
    @Mock OutboxEventRepository    outboxRepository;
    @Mock ServiceRequestRepository requestRepository;
    @Mock ProposalRepository       proposalRepository;
    @Mock UserRepository           userRepository;

    PaymentService service;

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                transactionRepository, outboxRepository,
                requestRepository, proposalRepository,
                userRepository, BigDecimal.valueOf(0.15));
    }

    /** Retorna um usuário com CPF hash registrado (identidade verificada). */
    private User clienteVerificado() {
        User u = User.builder().nome("Cliente").email("c@test.com")
                .senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        u.setCpfHash("abc123hashfake");
        return u;
    }

    @Test
    void initiate_criaTransacaoEOutboxAtomicamente() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, BigDecimal.valueOf(250))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-1", CLIENTE_ID);

        verify(transactionRepository).save(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void initiate_outboxTipoEStatusCorretos() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, BigDecimal.valueOf(200))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-2", CLIENTE_ID);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_INITIATED");
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxStatus.PENDENTE);
    }

    @Test
    void initiate_idempotente_retornaExistente() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var existing = new Transaction(UUID.randomUUID(), BigDecimal.valueOf(250),
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-dup");
        when(transactionRepository.findByIdempotencyKey("idem-dup")).thenReturn(Optional.of(existing));

        service.initiate(UUID.randomUUID(), new InitiatePaymentRequest("PIX"), "idem-dup", CLIENTE_ID);

        verify(transactionRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void initiate_pedidoNaoAceito_lancaBusinessException() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-3", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_NOT_ALLOWED");
    }

    @Test
    void initiate_clienteErrado_retornaNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.of(clienteVerificado()));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(requestRepository.findByIdAndCliente_Id(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.initiate(UUID.randomUUID(), new InitiatePaymentRequest("PIX"), "idem-6", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");
    }

    @Test
    void confirmPayment_statusPago_atualizaParaRetido() {
        var tx = new Transaction(UUID.randomUUID(), BigDecimal.valueOf(200),
                BigDecimal.valueOf(30), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-4");
        when(transactionRepository.findByGatewayTransactionId("gw-123")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmPayment("gw-123", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
    }

    @Test
    void confirmPayment_statusRejeitado_mantemPendente() {
        var tx = new Transaction(UUID.randomUUID(), BigDecimal.valueOf(200),
                BigDecimal.valueOf(30), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-5");
        when(transactionRepository.findByGatewayTransactionId("gw-456")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmPayment("gw-456", "REJEITADO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.PENDENTE);
    }

    // helpers
    private ServiceRequest serviceRequest(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        return sr;
    }

    private Proposal proposalAceita(ServiceRequest sr, BigDecimal valor) {
        return new Proposal(sr, UUID.randomUUID(), valor, 3, ProposalStatus.ACEITA);
    }
}
