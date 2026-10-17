package com.onda.marketplace.payment;

import com.onda.marketplace.auth.CpfHashService;
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

    /** Real: o que se prova é a versão da chave, e um mock não a teria. */
    final CpfHashService cpfHashService = new CpfHashService("test-cpf-hmac-key-0123456789-0123456789", 2);

    PaymentService service;

    private static final UUID CLIENTE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                transactionRepository, outboxRepository,
                requestRepository, proposalRepository,
                userRepository, cpfHashService, BigDecimal.valueOf(0.15), mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    /** Retorna um usuário com CPF hash registrado (identidade verificada). */
    private User clienteVerificado() {
        User u = User.builder().nome("Cliente").email("c@test.com")
                .senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        u.vincularCpf("abc123hashfake", cpfHashService.versaoAtual());
        return u;
    }

    @Test
    void initiate_criaTransacaoEOutboxAtomicamente() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
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
                userRepository, cpfHashService, new BigDecimal("0.10"), mock(org.springframework.context.ApplicationEventPublisher.class));
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
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
                userRepository, cpfHashService, new BigDecimal(percentual), mock(org.springframework.context.ApplicationEventPublisher.class));
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
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
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
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
    void initiate_hashDeChaveAntiga_pedeConfirmarDeNovo() {
        // rotação do HMAC: o hash do cliente está na versão anterior. Só ele sabe o CPF em claro, então o 1º pagamento depois
        // da troca pede a confirmação outra vez (que regrava o hash com a chave atual).
        User u = User.builder().nome("Cliente").email("c@test.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        u.vincularCpf("hash-da-chave-antiga", cpfHashService.versaoAtual() - 1);
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.initiate(UUID.randomUUID(), new InitiatePaymentRequest("PIX"), "idem-rot", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDENTITY_REQUIRED");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void initiate_semCpfConfirmado_pedeConfirmar() {
        User u = User.builder().nome("Cliente").email("c@test.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.initiate(UUID.randomUUID(), new InitiatePaymentRequest("PIX"), "idem-sem", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDENTITY_REQUIRED");
    }

    @Test
    void initiate_idempotente_retornaExistente() {
        // (o cliente nem é consultado num hit de idempotência: o portão de identidade vale só para criar cobrança nova)
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        var existing = new Transaction(sr.getId(), BigDecimal.valueOf(250),
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-dup");
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(sr.getId(), "idem-dup"))
                .thenReturn(Optional.of(existing));

        service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-dup", CLIENTE_ID);

        verify(transactionRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void initiate_retryIdempotenteDeClienteComHashDeChaveAntiga_devolveATransacaoJaCriada() {
        // Revisão cruzada de 2026-10-11 (Codex): o portão de identidade rodava ANTES da busca por idempotência, então depois
        // de uma rotação do HMAC um retry com a MESMA chave (cobrança já criada e só repetida pelo cliente) recebia
        // IDENTITY_REQUIRED em vez da transação. Reenviar uma requisição já atendida tem de devolver o mesmo resultado; o portão
        // vale só para CRIAR cobrança nova. A posse continua conferida antes de devolver qualquer coisa.
        User u = User.builder().nome("Cliente").email("c@test.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        u.vincularCpf("hash-da-chave-antiga", cpfHashService.versaoAtual() - 1);
        // lenient: o cliente com hash antigo é a CONDIÇÃO do teste (com o portão antes da idempotência ele barrava o retry),
        // mas o código certo nem chega a consultá-lo num hit
        lenient().when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(u));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        var existing = new Transaction(sr.getId(), BigDecimal.valueOf(250),
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-rot-retry");
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(sr.getId(), "idem-rot-retry"))
                .thenReturn(Optional.of(existing));

        var dto = service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-rot-retry", CLIENTE_ID);

        assertThat(dto).isNotNull();
        verify(transactionRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void initiate_hashDeChaveAntiga_comChaveDeIdempotenciaNova_aindaPedeConfirmarDeNovo() {
        // contraparte do teste acima: o portão continua barrando a CRIAÇÃO de cobrança nova (pedido do próprio cliente, sem hit)
        User u = User.builder().nome("Cliente").email("c@test.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        u.vincularCpf("hash-da-chave-antiga", cpfHashService.versaoAtual() - 1);
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(u));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(sr.getId(), new InitiatePaymentRequest("PIX"), "idem-rot-nova", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDENTITY_REQUIRED");
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
        when(requestRepository.findByIdAndClienteId(pedidoAlheio, CLIENTE_ID))
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
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
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
        when(requestRepository.findByIdAndClienteId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.initiate(UUID.randomUUID(), new InitiatePaymentRequest("PIX"), "idem-6", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");
    }

    /** O pedido da transação travado como 1ª leitura (revisão cruzada, 2ª rodada): o id sai de uma consulta escalar. */
    private void pedidoTravado(String gatewayId, UUID srId, ServiceRequestStatus status) {
        when(transactionRepository.findServiceRequestIdByGatewayTransactionId(gatewayId)).thenReturn(Optional.of(srId));
        when(requestRepository.findByIdComTrava(srId)).thenReturn(Optional.of(serviceRequest(status)));
    }

    private Transaction transacao(UUID srId, String chave) {
        return new Transaction(srId, BigDecimal.valueOf(200), BigDecimal.valueOf(30), BigDecimal.valueOf(0.15),
                PaymentMethod.PIX, chave);
    }

    @Test
    void confirmPayment_statusPago_atualizaParaRetido() {
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-4");
        pedidoTravado("gw-123", srId, ServiceRequestStatus.ACEITO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-123")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmPayment("gw-123", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
    }

    @Test
    void confirmPayment_pedidoJaCancelado_retemEEnfileiraOReembolso_masSoViraReembolsadoQuandoOGatewayDevolver() {
        // Achado da revisão cruzada (2026-10-05): cancel() pode ter cancelado o pedido enquanto o pagamento já
        // estava a caminho (cobrança enfileirada antes do cancelamento). Sem isto, o dinheiro ficaria retido pra
        // sempre: cancel() não encontrou transação RETIDA na hora (ela ainda nem existia) e não criou reembolso.
        // Revisão cruzada de 2026-10-11 (Codex): a transação NÃO pode ser gravada REEMBOLSADO aqui — o gateway ainda não
        // devolveu nada. Fica RETIDO (como em cancel()) até o OutboxProcessor concluir o reembolso; se o evento cair em
        // FALHA o estado continua dizendo a verdade ("retido, reembolso pendente"), que é também o que a exclusão de
        // conta consulta (clienteTemReembolsoPendente = pedido CANCELADO + transação RETIDO).
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-cancelado");
        pedidoTravado("gw-cancelado", srId, ServiceRequestStatus.CANCELADO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-cancelado")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmPayment("gw-cancelado", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_REFUNDED");
    }

    @Test
    void confirmPayment_reentregaDoWebhookDepoisDaReconciliacao_eNoOp_naoEstouraNemReembolsaDeNovo() {
        // 2ª rodada: depois que o outbox conclui o reembolso a transação está REEMBOLSADA. O gateway reentrega o MESMO "PAGO" (é normal): reter()
        // só era no-op para RETIDO, então a reentrega estourava INVALID_PAYMENT_TRANSITION (422 ao gateway, que reentrega
        // para sempre). Agora confirmação de transação que já não está PENDENTE é no-op.
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-reentrega");
        tx.reter();
        tx.reembolsar();
        pedidoTravado("gw-reentrega", srId, ServiceRequestStatus.CANCELADO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-reentrega")).thenReturn(Optional.of(tx));

        assertThatCode(() -> service.confirmPayment("gw-reentrega", "PAGO")).doesNotThrowAnyException();

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.REEMBOLSADO);
        verifyNoInteractions(outboxRepository);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmPayment_transacaoJaRetidaEPedidoCancelado_naoEnfileiraUmSegundoReembolso() {
        // 2ª rodada: cancel() viu a transação RETIDA e JÁ enfileirou o PAYMENT_REFUNDED. Uma reentrega do "PAGO" com a
        // transação ainda RETIDA (o outbox não processou) não pode enfileirar outro: dois reembolsos ao gateway.
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-ja-retida");
        tx.reter();
        pedidoTravado("gw-ja-retida", srId, ServiceRequestStatus.CANCELADO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-ja-retida")).thenReturn(Optional.of(tx));

        service.confirmPayment("gw-ja-retida", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void confirmPayment_travaOPedidoAntesDeLerATransacao_aTravaEAPrimeiraLeitura() {
        // a transação só é carregada DEPOIS da trava do pedido: o estado lido é o de agora (uma entrega duplicada simultânea
        // espera a primeira e vê RETIDO/REEMBOLSADO, em vez de as duas verem PENDENTE e enfileirarem dois reembolsos)
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-ordem");
        pedidoTravado("gw-ordem", srId, ServiceRequestStatus.ACEITO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-ordem")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmPayment("gw-ordem", "PAGO");

        var ordem = inOrder(transactionRepository, requestRepository);
        ordem.verify(transactionRepository).findServiceRequestIdByGatewayTransactionId("gw-ordem");
        ordem.verify(requestRepository).findByIdComTrava(srId);
        ordem.verify(transactionRepository).findByGatewayTransactionIdComTrava("gw-ordem");
        verify(requestRepository, never()).findById(any());
    }

    @Test
    void confirmPayment_transacaoDesconhecida_naoFazNada() {
        when(transactionRepository.findServiceRequestIdByGatewayTransactionId("gw-x")).thenReturn(Optional.empty());

        service.confirmPayment("gw-x", "PAGO");

        verifyNoInteractions(requestRepository, outboxRepository);
    }

    @Test
    void confirmPayment_pedidoAindaAtivo_retemNormalmente_semReembolso() {
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-ativo");
        pedidoTravado("gw-ativo", srId, ServiceRequestStatus.ACEITO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-ativo")).thenReturn(Optional.of(tx));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.confirmPayment("gw-ativo", "PAGO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void confirmPayment_statusRejeitado_mantemPendente() {
        var srId = UUID.randomUUID();
        var tx = transacao(srId, "idem-5");
        pedidoTravado("gw-456", srId, ServiceRequestStatus.ACEITO);
        when(transactionRepository.findByGatewayTransactionIdComTrava("gw-456")).thenReturn(Optional.of(tx));

        service.confirmPayment("gw-456", "REJEITADO");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.PENDENTE);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void initiate_comCartao_salvaCamposDeCartaoEOutbox() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, BigDecimal.valueOf(300))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new InitiatePaymentRequest("CARTAO", "tok_test_abc123", 3, "visa");
        var dto = service.initiate(sr.getId(), req, "idem-cartao-1", CLIENTE_ID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getMetodo()).isEqualTo(PaymentMethod.CARTAO);
        assertThat(saved.getCardToken()).isEqualTo("tok_test_abc123");
        assertThat(saved.getParcelas()).isEqualTo(3);
        assertThat(saved.getBandeira()).isEqualTo("visa");

        assertThat(dto.metodo()).isEqualTo("CARTAO");
        assertThat(dto.parcelas()).isEqualTo(3);
        assertThat(dto.bandeira()).isEqualTo("visa");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTipoEvento()).isEqualTo("PAYMENT_INITIATED");
    }

    @Test
    void initiate_comCartao_parcelasInvalidas_lancaBusinessException() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(sr.getId(),
                new InitiatePaymentRequest("CARTAO", "tok_123", 0, "master"), "idem-p0", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_INSTALLMENTS");

        assertThatThrownBy(() -> service.initiate(sr.getId(),
                new InitiatePaymentRequest("CARTAO", "tok_123", 13, "master"), "idem-p13", CLIENTE_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_INSTALLMENTS");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void initiate_comCartao_semParcelasOuBandeira_assumeValoresPadrao() {
        when(userRepository.findById(CLIENTE_ID)).thenReturn(Optional.of(clienteVerificado()));
        var sr = serviceRequest(ServiceRequestStatus.ACEITO);
        when(requestRepository.findByIdAndClienteId(any(), eq(CLIENTE_ID))).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(proposalRepository.findByServiceRequestIdAndStatus(any(), eq(ProposalStatus.ACEITA)))
                .thenReturn(List.of(proposalAceita(sr, BigDecimal.valueOf(150))));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new InitiatePaymentRequest("CARTAO");
        var dto = service.initiate(sr.getId(), req, "idem-cartao-default", CLIENTE_ID);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getMetodo()).isEqualTo(PaymentMethod.CARTAO);
        assertThat(saved.getParcelas()).isEqualTo(1);
        assertThat(saved.getCardToken()).isNull();
        assertThat(saved.getBandeira()).isNull();
        assertThat(dto.parcelas()).isEqualTo(1);
    }

    // helpers
    private ServiceRequest serviceRequest(ServiceRequestStatus status) {
        var sr = new ServiceRequest();
        sr.setStatus(status);
        sr.setCategoria("ELETRICISTA");
        return sr;
    }

    private Proposal proposalAceita(ServiceRequest sr, BigDecimal valor) {
        return new Proposal(sr.getId(), UUID.randomUUID(), valor, 3, null, ProposalStatus.ACEITA);
    }
}
