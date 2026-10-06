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
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, BigDecimal.valueOf(250))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-1", CLIENTE_ID);

        verify(transactionRepository).save(any());
        verify(outboxRepository).save(any());
    }

    @Test
    void initiate_cobraOValorDaProposta_eAComissaoSaiDoRepasseDoPrestador() {
        // Piloto: 10%. O cliente paga a proposta inteira (R$ 200); a comissão (R$ 20) é
        // retida da parte do prestador no repasse — não é somada à cobrança.
        var servico = new PaymentService(
                transactionRepository, outboxRepository,
                requestRepository, proposalRepository,
                userRepository, new BigDecimal("0.10"));
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, BigDecimal.valueOf(200))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        servico.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-comissao", CLIENTE_ID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getValorTotal()).isEqualByComparingTo("200.00");
        assertThat(captor.getValue().getValorComissao()).isEqualByComparingTo("20.00");
    }

    /** Comissão que o POST /payment devolve (o DTO) para uma proposta e um percentual. */
    private BigDecimal comissaoDevolvidaSobre(String valorProposta, String percentual) {
        var servico = new PaymentService(
                transactionRepository, outboxRepository,
                requestRepository, proposalRepository,
                userRepository, new BigDecimal(percentual));
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, new BigDecimal(valorProposta))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        return servico.initiate(sr.getId(), new InitiatePaymentRequest("PIX"),
                "idem-" + valorProposta + "-" + percentual, CLIENTE_ID).valorComissao();
    }

    @Test
    void initiate_arredondaAComissaoEmCentavosComOMeioParaCima() {
        // O banco guarda valor_comissao em NUMERIC(12,2) e arredonda o meio pra cima. Sem
        // arredondar aqui, a resposta do pagamento trazia 1.0050 enquanto o repasse usava 1.01
        // (o app do prestador mostra "Você recebe" com essa mesma regra).
        assertThat(comissaoDevolvidaSobre("10.05", "0.10"))
                .as("10,05 a 10% = 1,005 → 1,01").isEqualTo(new BigDecimal("1.01"));
        assertThat(comissaoDevolvidaSobre("0.04", "0.10"))
                .as("0,04 a 10% = 0,004 → 0,00").isEqualTo(new BigDecimal("0.00"));
        assertThat(comissaoDevolvidaSobre("99.99", "0.15"))
                .as("99,99 a 15% = 14,9985 → 15,00").isEqualTo(new BigDecimal("15.00"));
        assertThat(comissaoDevolvidaSobre("200.00", "0.10"))
                .as("valor exato continua exato").isEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void initiate_outboxTipoEStatusCorretos() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
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
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        var existing = new Transaction(sr.getId(), BigDecimal.valueOf(250),
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-dup");
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(sr.getId(), "idem-dup"))
                .thenReturn(Optional.of(existing));

        service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-dup", CLIENTE_ID);

        verify(transactionRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void initiate_hitDeIdempotenciaEmPedidoAlheio_naoVazaTransacao() {
        // Regressão: a posse só era conferida no caminho de CRIAÇÃO. Num hit de idempotência
        // o serviço devolvia a transação (valores + status) sem checar dono nenhum, então uma
        // colisão de chave entregava os dados financeiros de outro cliente.
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        UUID pedidoAlheio = UUID.randomUUID();
        when(requestRepository.findByIdAndCliente_Id(pedidoAlheio, CLIENTE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(
                pedidoAlheio, new InitiatePaymentRequest("PIX"), "idem-colidida", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");

        verify(transactionRepository, never()).findByServiceRequestIdAndIdempotencyKey(any(), any());
    }

    @Test
    void initiate_pedidoNaoAceito_lancaBusinessException() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.PENDENTE);
        when(requestRepository.findByIdAndCliente_Id(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-3", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYMENT_NOT_ALLOWED");
    }

    @Test
    void initiate_clienteErrado_retornaNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.of(clienteVerificado()));
        // Sem stub de idempotência: a posse é checada ANTES, então a busca nem é alcançada.
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
    void confirmPayment_pedidoJaCancelado_retemEDevolveNaMesmaHora() {
        // Achado da revisão cruzada (2026-10-05): cancel() pode ter cancelado o pedido enquanto o pagamento já
        // estava a caminho (cobrança enfileirada antes do cancelamento). Sem isto, o dinheiro ficaria retido pra
        // sempre: cancel() não encontrou transação RETIDA na hora (ela ainda nem existia) e não criou reembolso.
        var srId = UUID.randomUUID();
        var tx = new Transaction(srId, BigDecimal.valueOf(200),
                BigDecimal.valueOf(30), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-cancelado");
        when(transactionRepository.findByGatewayTransactionId("gw-cancelado")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(requestRepository.findById(srId)).thenReturn(Optional.of(serviceRequest(ServiceRequestStatus.CANCELADO)));

        service.confirmPayment("gw-cancelado", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.REEMBOLSADO);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    void confirmPayment_pedidoAindaAtivo_retemNormalmente_semReembolso() {
        var srId = UUID.randomUUID();
        var tx = new Transaction(srId, BigDecimal.valueOf(200),
                BigDecimal.valueOf(30), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-ativo");
        when(transactionRepository.findByGatewayTransactionId("gw-ativo")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(requestRepository.findById(srId)).thenReturn(Optional.of(serviceRequest(ServiceRequestStatus.ACEITO)));

        service.confirmPayment("gw-ativo", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        verifyNoInteractions(outboxRepository);
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
        return new Proposal(sr, UUID.randomUUID(), valor, 3, null, ProposalStatus.ACEITA);
    }
}
