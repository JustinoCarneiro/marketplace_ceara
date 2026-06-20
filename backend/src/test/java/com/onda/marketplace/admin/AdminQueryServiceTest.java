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

    AdminQueryService service;

    private static final UUID SR_ID = UUID.randomUUID();
    private static final UUID TX_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminQueryService(
                srRepository, transactionRepository, outboxRepository, resolutionRepository);
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

        DisputaDetalheDto dto = service.findDetalheDisputa(SR_ID);

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
    }

    // --- reprocessarOutbox ---

    @Test
    void reprocessarOutbox_falha_resetaParaPendente() {
        var ev = new OutboxEvent("transaction", TX_ID, "PAYMENT_RELEASED", "{}");
        ev.marcarFalha();
        when(outboxRepository.findById(any())).thenReturn(Optional.of(ev));
        when(outboxRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.reprocessarOutbox(UUID.randomUUID());

        assertThat(ev.getStatus()).isEqualTo(OutboxStatus.PENDENTE);
        verify(outboxRepository).save(ev);
    }

    @Test
    void reprocessarOutbox_processado_lancaException() {
        var ev = new OutboxEvent("transaction", TX_ID, "PAYMENT_RELEASED", "{}");
        ev.marcarProcessado();
        when(outboxRepository.findById(any())).thenReturn(Optional.of(ev));

        assertThatThrownBy(() -> service.reprocessarOutbox(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "OUTBOX_NOT_REPROCESSABLE");
    }

    @Test
    void reprocessarOutbox_naoEncontrado_lancaException() {
        when(outboxRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reprocessarOutbox(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "OUTBOX_NOT_FOUND");
    }

    // helpers

    // ServiceRequest.getId() retorna null sem JPA; usamos mock.
    // lenient() nos stubs do helper porque nem todo teste chama todos os getters
    // (ex: findDisputas não chama getStatus(), mas findDetalheDisputa chama).
    private ServiceRequest srMock(UUID id, ServiceRequestStatus status) {
        var sr = mock(ServiceRequest.class);
        lenient().when(sr.getId()).thenReturn(id);
        lenient().when(sr.getStatus()).thenReturn(status);
        lenient().when(sr.getCategoria()).thenReturn("ENCANADOR");
        lenient().when(sr.getCreatedAt()).thenReturn(Instant.now());
        return sr;
    }

    private Transaction transaction(UUID srId, BigDecimal valor) {
        var tx = new Transaction(srId, valor, valor.multiply(BigDecimal.valueOf(0.15)),
                BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-" + srId);
        tx.reter();
        return tx;
    }
}
