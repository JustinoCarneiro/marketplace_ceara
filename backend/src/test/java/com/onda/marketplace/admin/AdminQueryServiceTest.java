package com.onda.marketplace.admin;

import com.onda.marketplace.audit.AuditService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.payment.OutboxEvent;
import com.onda.marketplace.payment.OutboxEventRepository;
import com.onda.marketplace.payment.OutboxStatus;
import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.proposal.Proposal;
import com.onda.marketplace.proposal.ProposalRepository;
import com.onda.marketplace.proposal.ProposalStatus;
import com.onda.marketplace.provider.ProviderProfile;
import com.onda.marketplace.servicerequest.ServiceMediaRepository;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AdminQueryServiceTest {

    @Mock ServiceRequestRepository  srRepository;
    @Mock TransactionRepository     transactionRepository;
    @Mock OutboxEventRepository     outboxRepository;
    @Mock DisputeResolutionRepository resolutionRepository;
    @Mock ProposalRepository        proposalRepository;
    @Mock ServiceMediaRepository    mediaRepository;
    @Mock UserRepository            userRepository;
    @Mock com.onda.marketplace.provider.ProviderProfileRepository providerProfileRepository;
    @Mock com.onda.marketplace.provider.CpfEncryptor cpfEncryptor;
    @Mock AuditService auditService;
    @Mock com.onda.marketplace.servicerequest.StorageService storageService;

    AdminQueryService service;

    private static final UUID SR_ID = UUID.randomUUID();
    private static final UUID TX_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminQueryService(
                srRepository, transactionRepository, outboxRepository, resolutionRepository,
                proposalRepository, mediaRepository, userRepository,
                providerProfileRepository, cpfEncryptor, auditService, storageService);
    }

    // --- findDisputas ---

    @Test
    void findDisputas_retornaListaComValorRetido() {
        // ServiceRequest.getId() retorna null sem JPA — usamos mock para controlar o id
        var sr = srMock(SR_ID, ServiceRequestStatus.EM_DISPUTA);
        var tx = transaction(SR_ID, BigDecimal.valueOf(300));
        when(srRepository.findByStatus(ServiceRequestStatus.EM_DISPUTA))
                .thenReturn(java.util.Collections.singletonList(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));

        List<DisputaAdminDto> lista = service.findDisputas();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).serviceRequestId()).isEqualTo(SR_ID);
        assertThat(lista.get(0).valorRetido()).isEqualByComparingTo("300");
    }

    @Test
    void findDisputas_semTransacao_valorRetidoNull() {
        var sr = srMock(SR_ID, ServiceRequestStatus.EM_DISPUTA);
        when(srRepository.findByStatus(ServiceRequestStatus.EM_DISPUTA))
                .thenReturn(java.util.Collections.singletonList(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.empty());

        List<DisputaAdminDto> lista = service.findDisputas();

        assertThat(lista.get(0).valorRetido()).isNull();
    }

    // --- findDetalheDisputa ---

    @Test
    void findDetalheDisputa_retornaDetalheComTransacaoEResolucao() {
        var sr = srMock(SR_ID, ServiceRequestStatus.CONCLUIDO);
        var tx = transaction(SR_ID, BigDecimal.valueOf(200));
        var res = new DisputeResolution(SR_ID, UUID.randomUUID(), MediationDecision.CONCLUIR, "ok");
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(sr));
        when(transactionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(tx));
        when(resolutionRepository.findByServiceRequestId(SR_ID)).thenReturn(Optional.of(res));

        when(mediaRepository.findByServiceRequest_Id(SR_ID)).thenReturn(List.of(
                new com.onda.marketplace.servicerequest.ServiceMedia(sr,
                        com.onda.marketplace.servicerequest.MediaType.FOTO, "s3://bucket/key")));
        when(storageService.readUrl("s3://bucket/key")).thenReturn("https://example.org/signed");
        DisputaDetalheDto dto = service.findDetalheDisputa(SR_ID);
        assertThat(dto.midias()).singleElement().extracting(DisputaDetalheDto.MidiaDto::url)
                .isEqualTo("https://example.org/signed");

        assertThat(dto.serviceRequestId()).isEqualTo(SR_ID);
        assertThat(dto.valorTotal()).isEqualByComparingTo("200");
        assertThat(dto.decisao()).isEqualTo("CONCLUIR");
    }

    @Test
    void findDetalheDisputa_srNaoEncontrado_lancaException() {
        when(srRepository.findById(SR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetalheDisputa(SR_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REQUEST_NOT_FOUND");
    }

    // --- findTransacoes ---

    @Test
    void findTransacoes_filtraPorStatus() {
        var tx = transaction(SR_ID, BigDecimal.valueOf(150));
        when(transactionRepository.findByStatusPagamento(TransactionStatus.RETIDO))
                .thenReturn(List.of(tx));

        List<TransacaoAdminDto> lista = service.findTransacoes(TransactionStatus.RETIDO);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).statusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        assertThat(lista.get(0).valorTotal()).isEqualByComparingTo("150");
    }

    // --- findOutbox ---

    @Test
    void findOutbox_filtraPorStatus() {
        var ev = new OutboxEvent("transaction", TX_ID, "PAYMENT_INITIATED", "{}");
        ev.marcarFalha();  // status = FALHA para bater com o que o DTO vai copiar
        when(outboxRepository.findByStatus(OutboxStatus.FALHA)).thenReturn(List.of(ev));

        List<OutboxAdminDto> lista = service.findOutbox(OutboxStatus.FALHA);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).tipoEvento()).isEqualTo("PAYMENT_INITIATED");
        assertThat(lista.get(0).status()).isEqualTo(OutboxStatus.FALHA);
        assertThat(lista.get(0).agregadoId()).isEqualTo(TX_ID); // MKT-49: liga o evento à transação
    }

    // --- reprocessarOutbox ---

    @Test
    void reprocessarOutbox_falha_resetaParaPendente() {
        var ev = new OutboxEvent("transaction", TX_ID, "PAYMENT_RELEASED", "{}");
        ev.marcarFalha();
        when(outboxRepository.findByIdComTrava(any())).thenReturn(Optional.of(ev));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.reprocessarOutbox(UUID.randomUUID());

        assertThat(ev.getStatus()).isEqualTo(OutboxStatus.PENDENTE);
        verify(outboxRepository).save(ev);
    }

    @Test
    void reprocessarOutbox_processado_lancaException() {
        var ev = new OutboxEvent("transaction", TX_ID, "PAYMENT_RELEASED", "{}");
        ev.marcarProcessado();
        when(outboxRepository.findByIdComTrava(any())).thenReturn(Optional.of(ev));

        assertThatThrownBy(() -> service.reprocessarOutbox(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "OUTBOX_NOT_REPROCESSABLE");
    }

    @Test
    void reprocessarOutbox_naoEncontrado_lancaException() {
        when(outboxRepository.findByIdComTrava(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reprocessarOutbox(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "OUTBOX_NOT_FOUND");
    }

    // --- findRepassePendente / confirmarRepasseManual (MKT-49) ---

    @Test
    void findRepassePendente_comChavePixCadastrada_devolveDecifrada() {
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(250));
        UUID prestadorId = UUID.randomUUID();
        var proposta = mock(Proposal.class);
        when(proposta.getPrestadorId()).thenReturn(prestadorId);
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
        perfil.setChavePixCifrada("cifrado-base64");
        var prestador = mock(User.class);
        when(prestador.getNome()).thenReturn("José");

        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta));
        when(userRepository.findById(prestadorId)).thenReturn(Optional.of(prestador));
        when(providerProfileRepository.findByUserId(prestadorId)).thenReturn(Optional.of(perfil));
        when(cpfEncryptor.decrypt("cifrado-base64")).thenReturn("jose@pix.com");

        RepassePendenteDto dto = service.findRepassePendente(TX_ID);

        assertThat(dto.prestadorNome()).isEqualTo("José");
        assertThat(dto.chavePixCadastrada()).isTrue();
        assertThat(dto.chavePix()).isEqualTo("jose@pix.com");
        assertThat(dto.valorARepassar()).isEqualByComparingTo("212.50"); // 250 - 15%
    }

    @Test
    void findRepassePendente_semChavePixCadastrada_naoDecifraNada() {
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        UUID prestadorId = UUID.randomUUID();
        var proposta = mock(Proposal.class);
        when(proposta.getPrestadorId()).thenReturn(prestadorId);

        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(tx));
        when(proposalRepository.findByServiceRequestIdAndStatus(SR_ID, ProposalStatus.ACEITA))
                .thenReturn(List.of(proposta));
        when(userRepository.findById(prestadorId)).thenReturn(Optional.empty());
        when(providerProfileRepository.findByUserId(prestadorId)).thenReturn(Optional.empty());

        RepassePendenteDto dto = service.findRepassePendente(TX_ID);

        assertThat(dto.chavePixCadastrada()).isFalse();
        assertThat(dto.chavePix()).isNull();
        verify(cpfEncryptor, never()).decrypt(any());
    }

    @Test
    void findRepassePendente_transacaoNaoRetida_lancaException() {
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        tx.liberar(); // RETIDO -> LIBERADO
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> service.findRepassePendente(TX_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "TRANSACTION_NOT_RETAINED");
    }

    @Test
    void confirmarRepasseManual_liberaATransacao_eFechaOEventoFalho() {
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        UUID adminId = UUID.randomUUID();
        var eventoFalho = new OutboxEvent("transaction", TX_ID, "PAYMENT_RELEASED", "{}");
        eventoFalho.marcarFalha();

        when(transactionRepository.findByIdComTrava(TX_ID)).thenReturn(Optional.of(tx));
        var pedido = srMock(SR_ID, ServiceRequestStatus.CONCLUIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(pedido));
        when(outboxRepository.findByAgregadoIdAndTipoEvento(TX_ID, "PAYMENT_RELEASED"))
                .thenReturn(List.of(eventoFalho));

        service.confirmarRepasseManual(TX_ID, adminId, "E2E-12345");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.LIBERADO);
        assertThat(eventoFalho.getStatus()).isEqualTo(OutboxStatus.PROCESSADO);
        verify(transactionRepository).save(tx);
        verify(outboxRepository).save(eventoFalho);
        verify(auditService).registrar(adminId, "CONFIRMAR_REPASSE_MANUAL", "transaction", TX_ID,
                "comprovante=E2E-12345");
    }

    @Test
    void confirmarRepasseManual_repetido_naoDuplicaAuditoria() {
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        tx.liberar();
        when(transactionRepository.findByIdComTrava(TX_ID)).thenReturn(Optional.of(tx));

        service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), "E2E-12345");

        verifyNoInteractions(auditService, outboxRepository);
    }

    @Test
    void confirmarRepasseManual_semReferencia_naoConsultaNemAlteraTransacao() {
        assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), " "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYOUT_RECEIPT_REQUIRED");
        verifyNoInteractions(transactionRepository, outboxRepository, auditService);
    }

    @Test
    void confirmarRepasseManual_referenciaQueParecaCpf_recusaSemLiberarNemAuditar() {
        // A referência é o identificador do comprovante, nunca dado pessoal: um CPF colado por engano (com ou sem máscara) iria para a auditoria,
        // que é retida e lida por todo admin. O padrão da API (letras, dígitos e . _ : / -) NÃO o barra — só a validação dos dígitos verificadores.
        for (String cpf : List.of("11144477735", "111.444.777-35")) {
            assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), cpf))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "PAYOUT_RECEIPT_INVALID");
        }
        verifyNoInteractions(transactionRepository, outboxRepository, auditService);
    }

    @Test
    void confirmarRepasseManual_referenciaQueParecaChavePixAleatoria_recusa() {
        assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYOUT_RECEIPT_INVALID");
        verifyNoInteractions(transactionRepository, outboxRepository, auditService);
    }

    @Test
    void confirmarRepasseManual_identificadorDeComprovanteDoPix_ehAceito() {
        // controle contra o excesso: o formato real do ID de uma transferência Pix (E + ISPB + data/hora + sufixo) passa
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        UUID adminId = UUID.randomUUID();
        when(transactionRepository.findByIdComTrava(TX_ID)).thenReturn(Optional.of(tx));
        var pedido = srMock(SR_ID, ServiceRequestStatus.CONCLUIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(pedido));

        service.confirmarRepasseManual(TX_ID, adminId, "E12345678202610141234abcdEFGHIJK");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.LIBERADO);
        verify(auditService).registrar(adminId, "CONFIRMAR_REPASSE_MANUAL", "transaction", TX_ID,
                "comprovante=E12345678202610141234abcdEFGHIJK");
    }

    @Test
    void confirmarRepasseManual_pedidoNaoConcluido_recusaSemLiberarNemAuditar() {
        // A API liberava QUALQUER transação RETIDO: um pedido em disputa ou em andamento teria o escrow dado como pago (e a mediação depois não
        // conseguiria reembolsar). A UI só mostra a fila certa, mas a regra vale na API (defesa em profundidade): repasse só de pedido CONCLUIDO.
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        when(transactionRepository.findByIdComTrava(TX_ID)).thenReturn(Optional.of(tx));
        var pedido = srMock(SR_ID, ServiceRequestStatus.EM_DISPUTA);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), "E2E-12345"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYOUT_NOT_DUE");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        verify(transactionRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(outboxRepository, auditService);
    }

    @ParameterizedTest
    @ValueSource(strings = {" 11144477735 ", "\t111.444.777-35\n",
            "01234567-89AB-CDEF-0123-456789ABCDEF", " 01234567-89ab-cdef-0123-456789abcdef "})
    void confirmarRepasseManual_normalizaAntesDeRecusarDadoPessoal(String referencia) {
        assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), referencia))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYOUT_RECEIPT_INVALID");

        verifyNoInteractions(transactionRepository, srRepository, outboxRepository, auditService);
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestStatus.class, names = "CONCLUIDO", mode = EnumSource.Mode.EXCLUDE)
    void confirmarRepasseManual_recusaTodosOsEstadosNaoConcluidos(ServiceRequestStatus status) {
        Transaction tx = transaction(SR_ID, BigDecimal.valueOf(100));
        when(transactionRepository.findByIdComTrava(TX_ID)).thenReturn(Optional.of(tx));
        var pedido = srMock(SR_ID, status);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), "REF-ESTADO"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PAYOUT_NOT_DUE");

        assertThat(tx.getStatusPagamento()).isEqualTo(TransactionStatus.RETIDO);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(outboxRepository, auditService);
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = {"PENDENTE", "REEMBOLSADO"})
    void confirmarRepasseManual_pedidoConcluidoNaoDispensaDinheiroRetido(TransactionStatus status) {
        var tx = new Transaction(SR_ID, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("0.10"),
                PaymentMethod.PIX, "idem-estado");
        if (status == TransactionStatus.REEMBOLSADO) {
            tx.reter();
            tx.reembolsar();
        }
        when(transactionRepository.findByIdComTrava(TX_ID)).thenReturn(Optional.of(tx));
        var pedido = srMock(SR_ID, ServiceRequestStatus.CONCLUIDO);
        when(srRepository.findById(SR_ID)).thenReturn(Optional.of(pedido));

        assertThatThrownBy(() -> service.confirmarRepasseManual(TX_ID, UUID.randomUUID(), "REF-ESTADO"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PAYMENT_TRANSITION");

        assertThat(tx.getStatusPagamento()).isEqualTo(status);
        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(outboxRepository, auditService);
    }

    // helpers

    // ServiceRequest.getId() retorna null sem JPA; usamos mock.
    // lenient() nos stubs do helper porque nem todo teste chama todos os getters
    // (ex: findDisputas não chama getStatus(), mas findDetalheDisputa chama).
    private ServiceRequest srMock(UUID id, ServiceRequestStatus status) {
        var sr = mock(ServiceRequest.class);
        var cliente = mock(User.class);
        lenient().when(cliente.getNome()).thenReturn("Cliente Teste");
        lenient().when(sr.getId()).thenReturn(id);
        lenient().when(sr.getStatus()).thenReturn(status);
        lenient().when(sr.getCategoria()).thenReturn("ENCANADOR");
        lenient().when(sr.getCreatedAt()).thenReturn(Instant.now());
        lenient().when(sr.getCliente()).thenReturn(cliente);
        return sr;
    }

    private Transaction transaction(UUID srId, BigDecimal valor) {
        var tx = new Transaction(srId, valor, valor.multiply(BigDecimal.valueOf(0.15)),
                BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-" + srId);
        tx.reter();
        return tx;
    }
}
