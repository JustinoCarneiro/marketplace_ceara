package com.onda.marketplace.admin;

import com.onda.marketplace.payment.PaymentMethod;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderStatus;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.sos.SosAlertRepository;
import com.onda.marketplace.sos.SosAlertStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock ServiceRequestRepository  srRepository;
    @Mock TransactionRepository     transactionRepository;
    @Mock ProviderProfileRepository providerProfileRepository;
    @Mock SosAlertRepository        sosRepository;

    AdminReportService service;

    @BeforeEach
    void setUp() {
        service = new AdminReportService(
                srRepository, transactionRepository, providerProfileRepository, sosRepository);
    }

    @Test
    void metrics_agregaContadoresEReceitaDeComissao() {
        when(srRepository.count()).thenReturn(42L);
        when(srRepository.countByStatus(ServiceRequestStatus.CONCLUIDO)).thenReturn(30L);
        when(srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA)).thenReturn(2L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO)).thenReturn(15L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO)).thenReturn(5L);
        when(transactionRepository.somaComissaoPorStatus(TransactionStatus.LIBERADO))
                .thenReturn(BigDecimal.valueOf(1234.50));

        MetricsDto m = service.metrics();

        assertThat(m.totalPedidos()).isEqualTo(42L);
        assertThat(m.pedidosConcluidos()).isEqualTo(30L);
        assertThat(m.pedidosEmDisputa()).isEqualTo(2L);
        assertThat(m.prestadoresVerificados()).isEqualTo(15L);
        assertThat(m.prestadoresEmVerificacao()).isEqualTo(5L);
        assertThat(m.receitaComissao()).isEqualByComparingTo("1234.50");
    }

    @Test
    void alertas_listaApenasOperacionaisComQuantidadePositiva() {
        when(sosRepository.countByStatus(SosAlertStatus.ATIVO)).thenReturn(1L);
        when(srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA)).thenReturn(3L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO)).thenReturn(0L);

        List<OperationalAlert> alertas = service.alertas();

        assertThat(alertas).extracting(OperationalAlert::tipo)
                .containsExactly("SOS_ATIVO", "DISPUTA_ABERTA");
        assertThat(alertas).extracting(OperationalAlert::quantidade)
                .containsExactly(1L, 3L);
    }

    @Test
    void alertas_semOcorrencias_retornaListaVazia() {
        when(sosRepository.countByStatus(SosAlertStatus.ATIVO)).thenReturn(0L);
        when(srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA)).thenReturn(0L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO)).thenReturn(0L);

        assertThat(service.alertas()).isEmpty();
    }

    @Test
    void exportarCsv_transactions_geraCabecalhoELinhas_semCpf() {
        var tx = new Transaction(java.util.UUID.randomUUID(), BigDecimal.valueOf(200),
                BigDecimal.valueOf(30), BigDecimal.valueOf(0.15), PaymentMethod.PIX, "idem-csv");
        when(transactionRepository.findAll()).thenReturn(List.of(tx));

        String csv = service.exportarCsv("transactions");

        assertThat(csv).startsWith("id,serviceRequestId,valorTotal,valorComissao,metodo,statusPagamento");
        assertThat(csv).contains("PIX").contains("200");
        // TS04/LGPD: relatório nunca expõe CPF
        assertThat(csv.toLowerCase()).doesNotContain("cpf");
    }

    @Test
    void exportarCsv_recursoDesconhecido_lancaException() {
        assertThatThrownBy(() -> service.exportarCsv("usuarios"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UNKNOWN_REPORT");
    }

    @Test
    void exportarMetricasPdf_retornaByteArrayNaoVazio_semCpf() {
        // arrange — mesmos mocks do teste de metrics()
        when(srRepository.count()).thenReturn(10L);
        when(srRepository.countByStatus(ServiceRequestStatus.CONCLUIDO)).thenReturn(8L);
        when(srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA)).thenReturn(1L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO)).thenReturn(5L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO)).thenReturn(0L);
        when(transactionRepository.somaComissaoPorStatus(TransactionStatus.LIBERADO))
                .thenReturn(java.math.BigDecimal.valueOf(500));

        byte[] pdf = service.exportarMetricasPdf();

        // PDF deve começar com assinatura PDF
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, Math.min(4, pdf.length))).startsWith("%PDF");

        // TS04/LGPD: relatório nunca expõe CPF
        String conteudoLegivel = new String(pdf);
        assertThat(conteudoLegivel.toLowerCase()).doesNotContain("cpf");
    }
}
