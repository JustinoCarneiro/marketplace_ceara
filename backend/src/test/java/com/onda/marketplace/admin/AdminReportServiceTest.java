package com.onda.marketplace.admin;

import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AdminReportServiceTest {

    @Mock ServiceRequestRepository    srRepository;
    @Mock TransactionRepository       transactionRepository;
    @Mock ProviderProfileRepository   providerProfileRepository;
    @Mock SosAlertRepository          sosRepository;
    @Mock UserRepository              userRepository;
    @Mock DisputeResolutionRepository resolutionRepository;

    AdminReportService service;

    @BeforeEach
    void setUp() {
        service = new AdminReportService(
                srRepository, transactionRepository, providerProfileRepository, sosRepository,
                userRepository, resolutionRepository);
    }

    /** Linha do GROUP BY status: [ServiceRequestStatus, Long]. */
    private static Object[] linha(ServiceRequestStatus status, long qtd) {
        return new Object[] { status, qtd };
    }

    @Test
    void metrics_agregaFluxoFinanceiroEPedidosPorStatus() {
        when(srRepository.contarPorStatusNoPeriodo(any(), any())).thenReturn(List.of(
                linha(ServiceRequestStatus.CONCLUIDO, 30L),
                linha(ServiceRequestStatus.PENDENTE, 10L),
                linha(ServiceRequestStatus.EM_DISPUTA, 2L)));
        when(transactionRepository.somaValorTotalNoPeriodo(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(10000));
        when(transactionRepository.contarNoPeriodo(any(), any(), any())).thenReturn(40L);
        when(transactionRepository.somaComissaoNoPeriodo(eq(TransactionStatus.LIBERADO), any(), any()))
                .thenReturn(BigDecimal.valueOf(1234.50));
        when(srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA)).thenReturn(2L);
        when(resolutionRepository.tempoMedioResolucaoHoras(any(), any())).thenReturn(12.5);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO)).thenReturn(15L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO)).thenReturn(5L);
        when(userRepository.countByRoleAndAtivoTrue(UserRole.ROLE_CLIENT)).thenReturn(120L);
        when(sosRepository.contarNoPeriodo(any(), any())).thenReturn(3L);

        MetricsDto m = service.metrics();

        assertThat(m.gmv()).isEqualByComparingTo("10000");
        assertThat(m.receitaComissao()).isEqualByComparingTo("1234.50");
        assertThat(m.ticketMedio()).isEqualByComparingTo("250.00");   // 10000 / 40
        assertThat(m.totalPedidos()).isEqualTo(42L);                  // 30 + 10 + 2
        assertThat(m.pedidosConcluidos()).isEqualTo(30L);
        assertThat(m.taxaConclusao()).isEqualTo(30.0 / 42.0);
        assertThat(m.pedidosPorStatus()).containsEntry("PENDENTE", 10L);
        assertThat(m.disputasAbertas()).isEqualTo(2L);
        assertThat(m.tempoMedioResolucaoHoras()).isEqualTo(12.5);
        assertThat(m.prestadoresVerificados()).isEqualTo(15L);
        assertThat(m.prestadoresAtivos()).isEqualTo(20L);             // verificados + em verificação
        assertThat(m.clientesAtivos()).isEqualTo(120L);
        assertThat(m.sosAcionados()).isEqualTo(3L);
    }

    @Test
    void metrics_semPedidoNenhum_naoDividePorZero() {
        when(srRepository.contarPorStatusNoPeriodo(any(), any())).thenReturn(List.of());
        when(transactionRepository.somaValorTotalNoPeriodo(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.contarNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(transactionRepository.somaComissaoNoPeriodo(eq(TransactionStatus.LIBERADO), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(resolutionRepository.tempoMedioResolucaoHoras(any(), any())).thenReturn(0.0);

        MetricsDto m = service.metrics();

        assertThat(m.totalPedidos()).isZero();
        assertThat(m.taxaConclusao()).isZero();
        assertThat(m.ticketMedio()).isEqualByComparingTo("0");
    }

    @Test
    void metrics_semPeriodo_consultaFaixaAbertaEmVezDeParametroNulo() {
        // Regressão: com null o Postgres estourava "could not determine data type of
        // parameter" em ":de IS NULL OR ...". Teste mockado não executa SQL, então a
        // proteção aqui é garantir que null nunca chega até a consulta.
        when(srRepository.contarPorStatusNoPeriodo(any(), any())).thenReturn(List.of());
        when(transactionRepository.somaValorTotalNoPeriodo(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.contarNoPeriodo(any(), any(), any())).thenReturn(0L);
        when(transactionRepository.somaComissaoNoPeriodo(eq(TransactionStatus.LIBERADO), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(resolutionRepository.tempoMedioResolucaoHoras(any(), any())).thenReturn(0.0);

        service.metrics(null, null);

        var de  = org.mockito.ArgumentCaptor.forClass(Instant.class);
        var ate = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(srRepository).contarPorStatusNoPeriodo(de.capture(), ate.capture());
        assertThat(de.getValue()).isNotNull().isEqualTo(Instant.EPOCH);
        assertThat(ate.getValue()).isNotNull().isAfter(Instant.now());
    }

    @Test
    void metrics_repassaOPeriodoParaAsConsultasDeFluxo() {
        Instant de  = Instant.parse("2026-07-01T00:00:00Z");
        Instant ate = Instant.parse("2026-08-01T00:00:00Z");
        when(srRepository.contarPorStatusNoPeriodo(de, ate)).thenReturn(List.of());
        when(transactionRepository.somaValorTotalNoPeriodo(any(), eq(de), eq(ate)))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.contarNoPeriodo(any(), eq(de), eq(ate))).thenReturn(0L);
        when(transactionRepository.somaComissaoNoPeriodo(TransactionStatus.LIBERADO, de, ate))
                .thenReturn(BigDecimal.ZERO);
        when(resolutionRepository.tempoMedioResolucaoHoras(de, ate)).thenReturn(0.0);

        service.metrics(de, ate);

        verify(sosRepository).contarNoPeriodo(de, ate);
        // estoque ignora o período de propósito: disputa aberta antiga ainda é problema de hoje
        verify(srRepository).countByStatus(ServiceRequestStatus.EM_DISPUTA);
        verify(userRepository).countByRoleAndAtivoTrue(UserRole.ROLE_CLIENT);
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
        when(srRepository.contarPorStatusNoPeriodo(any(), any())).thenReturn(List.of(
                linha(ServiceRequestStatus.CONCLUIDO, 8L),
                linha(ServiceRequestStatus.EM_DISPUTA, 1L)));
        when(transactionRepository.somaValorTotalNoPeriodo(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(3000));
        when(transactionRepository.contarNoPeriodo(any(), any(), any())).thenReturn(10L);
        when(transactionRepository.somaComissaoNoPeriodo(eq(TransactionStatus.LIBERADO), any(), any()))
                .thenReturn(BigDecimal.valueOf(500));
        when(srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA)).thenReturn(1L);
        when(resolutionRepository.tempoMedioResolucaoHoras(any(), any())).thenReturn(6.0);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO)).thenReturn(5L);
        when(providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO)).thenReturn(0L);
        when(userRepository.countByRoleAndAtivoTrue(UserRole.ROLE_CLIENT)).thenReturn(40L);
        when(sosRepository.contarNoPeriodo(any(), any())).thenReturn(0L);

        byte[] pdf = service.exportarMetricasPdf();

        // PDF deve começar com assinatura PDF
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, Math.min(4, pdf.length))).startsWith("%PDF");

        // TS04/LGPD: relatório nunca expõe CPF
        String conteudoLegivel = new String(pdf);
        assertThat(conteudoLegivel.toLowerCase()).doesNotContain("cpf");
    }
}
