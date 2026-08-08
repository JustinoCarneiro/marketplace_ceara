package com.onda.marketplace.admin;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.payment.Transaction;
import com.onda.marketplace.payment.TransactionRepository;
import com.onda.marketplace.payment.TransactionStatus;
import com.onda.marketplace.provider.ProviderProfileRepository;
import com.onda.marketplace.provider.ProviderStatus;
import com.onda.marketplace.servicerequest.ServiceRequest;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import com.onda.marketplace.servicerequest.ServiceRequestStatus;
import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.sos.SosAlertRepository;
import com.onda.marketplace.sos.SosAlertStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Métricas, alertas operacionais e exportação de relatórios do painel admin
 * (US23/US29/US30). Tudo derivado por agregação — sem tabela de verdade
 * financeira (TS09). Relatórios NUNCA expõem CPF (TS04/LGPD).
 */
@Service
public class AdminReportService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AdminReportService.class);

    /** GMV = dinheiro que entrou no escrow e não voltou pro cliente. */
    private static final List<TransactionStatus> STATUS_GMV =
            List.of(TransactionStatus.RETIDO, TransactionStatus.LIBERADO);

    /**
     * "Sem filtro" vira uma faixa aberta em vez de parâmetro nulo: o Postgres não infere
     * o tipo de um NULL solto em {@code :param IS NULL OR ...} e a consulta estourava
     * ("could not determine data type of parameter"). Teste mockado não pega isso —
     * JPQL só é executada de verdade contra o banco.
     */
    private static final Instant INICIO_DOS_TEMPOS = Instant.EPOCH;
    private static final Instant FIM_DOS_TEMPOS    = Instant.parse("9999-12-31T00:00:00Z");

    private final ServiceRequestRepository    srRepository;
    private final TransactionRepository       transactionRepository;
    private final ProviderProfileRepository   providerProfileRepository;
    private final SosAlertRepository          sosRepository;
    private final UserRepository              userRepository;
    private final DisputeResolutionRepository resolutionRepository;

    public AdminReportService(ServiceRequestRepository srRepository,
                              TransactionRepository transactionRepository,
                              ProviderProfileRepository providerProfileRepository,
                              SosAlertRepository sosRepository,
                              UserRepository userRepository,
                              DisputeResolutionRepository resolutionRepository) {
        this.srRepository              = srRepository;
        this.transactionRepository     = transactionRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.sosRepository             = sosRepository;
        this.userRepository            = userRepository;
        this.resolutionRepository      = resolutionRepository;
    }

    /** Métricas sem recorte de período (todo o histórico). */
    @Transactional(readOnly = true)
    public MetricsDto metrics() {
        return metrics(null, null);
    }

    /**
     * Métricas do dashboard (US23) com filtro de período opcional.
     * O recorte vale para as métricas de fluxo; as de estoque descrevem o estado
     * atual e ignoram as datas — ver {@link MetricsDto}.
     *
     * @param de  início do período (inclusive), ou null para "desde sempre"
     * @param ate fim do período (exclusive), ou null para "até agora"
     */
    @Transactional(readOnly = true)
    public MetricsDto metrics(Instant deOuNull, Instant ateOuNull) {
        Instant de  = deOuNull  != null ? deOuNull  : INICIO_DOS_TEMPOS;
        Instant ate = ateOuNull != null ? ateOuNull : FIM_DOS_TEMPOS;

        Map<String, Long> pedidosPorStatus = new LinkedHashMap<>();
        for (Object[] linha : srRepository.contarPorStatusNoPeriodo(de, ate)) {
            pedidosPorStatus.put(((ServiceRequestStatus) linha[0]).name(), (Long) linha[1]);
        }

        long totalPedidos = pedidosPorStatus.values().stream().mapToLong(Long::longValue).sum();
        long concluidos   = pedidosPorStatus.getOrDefault(ServiceRequestStatus.CONCLUIDO.name(), 0L);
        double taxaConclusao = totalPedidos == 0 ? 0.0 : (double) concluidos / totalPedidos;

        BigDecimal gmv          = transactionRepository.somaValorTotalNoPeriodo(STATUS_GMV, de, ate);
        long       qtdNoGmv     = transactionRepository.contarNoPeriodo(STATUS_GMV, de, ate);
        BigDecimal ticketMedio  = qtdNoGmv == 0
                ? BigDecimal.ZERO
                : gmv.divide(BigDecimal.valueOf(qtdNoGmv), 2, RoundingMode.HALF_UP);

        return new MetricsDto(
                gmv,
                transactionRepository.somaComissaoNoPeriodo(TransactionStatus.LIBERADO, de, ate),
                ticketMedio,
                pedidosPorStatus,
                taxaConclusao,
                // estoque: disputa aberta é problema atual, mesmo que o pedido seja antigo
                srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA),
                resolutionRepository.tempoMedioResolucaoHoras(de, ate),
                providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO),
                providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO)
                        + providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO),
                userRepository.countByRoleAndAtivoTrue(UserRole.ROLE_CLIENT),
                sosRepository.contarNoPeriodo(de, ate));
    }

    @Transactional(readOnly = true)
    public List<OperationalAlert> alertas() {
        List<OperationalAlert> alertas = new ArrayList<>();
        addSePositivo(alertas, "SOS_ATIVO",
                sosRepository.countByStatus(SosAlertStatus.ATIVO));
        addSePositivo(alertas, "DISPUTA_ABERTA",
                srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA));
        addSePositivo(alertas, "VERIFICACAO_INCONCLUSIVA",
                providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO));
        return alertas;
    }

    private void addSePositivo(List<OperationalAlert> alertas, String tipo, long quantidade) {
        if (quantidade > 0) {
            alertas.add(new OperationalAlert(tipo, quantidade));
        }
    }

    @Transactional(readOnly = true)
    public String exportarCsv(String recurso) {
        return switch (recurso) {
            case "transactions" -> exportarTransacoes();
            case "requests"     -> exportarPedidos();
            default -> throw new BusinessException("UNKNOWN_REPORT",
                    "Relatório desconhecido: " + recurso);
        };
    }

    private String exportarTransacoes() {
        StringBuilder sb = new StringBuilder(
                "id,serviceRequestId,valorTotal,valorComissao,metodo,statusPagamento,criadoEm");
        for (Transaction t : transactionRepository.findAll()) {
            sb.append('\n').append(linha(
                    t.getId(), t.getServiceRequestId(), t.getValorTotal(), t.getValorComissao(),
                    t.getMetodo(), t.getStatusPagamento(), t.getCreatedAt()));
        }
        return sb.toString();
    }

    private String exportarPedidos() {
        StringBuilder sb = new StringBuilder("id,categoria,status,criadoEm");
        for (ServiceRequest s : srRepository.findAll()) {
            sb.append('\n').append(linha(
                    s.getId(), s.getCategoria(), s.getStatus(), s.getCreatedAt()));
        }
        return sb.toString();
    }

    private String linha(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.valueOf(campos[i]));
        }
        return sb.toString();
    }

    /**
     * Gera PDF em memória com resumo de métricas do painel (US29).
     * NUNCA expõe CPF — somente agregados (TS04/LGPD).
     *
     * @return array de bytes do PDF
     */
    @Transactional(readOnly = true)
    public byte[] exportarMetricasPdf() {
        MetricsDto m = metrics();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Título
            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            doc.add(new Paragraph("Marketplace Ceará — Relatório de Métricas", tituloFont));
            doc.add(new Paragraph("Gerado em: " + java.time.Instant.now()));
            doc.add(Chunk.NEWLINE);

            // Métricas
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font valorFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            adicionarLinha(doc, labelFont, valorFont, "Total de pedidos",          String.valueOf(m.totalPedidos()));
            adicionarLinha(doc, labelFont, valorFont, "Pedidos concluídos",        String.valueOf(m.pedidosConcluidos()));
            adicionarLinha(doc, labelFont, valorFont, "Taxa de conclusão",
                    String.format(java.util.Locale.ROOT, "%.1f%%", m.taxaConclusao() * 100));
            adicionarLinha(doc, labelFont, valorFont, "Disputas abertas",          String.valueOf(m.disputasAbertas()));
            adicionarLinha(doc, labelFont, valorFont, "Volume transacionado (R$)", m.gmv().toPlainString());
            adicionarLinha(doc, labelFont, valorFont, "Ticket médio (R$)",         m.ticketMedio().toPlainString());
            adicionarLinha(doc, labelFont, valorFont, "Receita de comissão (R$)",  m.receitaComissao().toPlainString());
            adicionarLinha(doc, labelFont, valorFont, "Prestadores verificados",   String.valueOf(m.prestadoresVerificados()));
            adicionarLinha(doc, labelFont, valorFont, "Clientes ativos",           String.valueOf(m.clientesAtivos()));
            adicionarLinha(doc, labelFont, valorFont, "SOS acionados",             String.valueOf(m.sosAcionados()));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            // Sem log a causa real some e sobra só "falha ao gerar PDF" — foi assim que um
            // NullPointerException vindo das métricas passou por erro do gerador de PDF.
            log.error("Falha ao gerar PDF de métricas", e);
            throw new BusinessException("PDF_GENERATION_FAILED", "Falha ao gerar PDF de métricas.");
        }
    }

    private void adicionarLinha(Document doc, Font label, Font valor,
                                 String chave, String conteudo) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(chave + ": ", label));
        p.add(new Chunk(conteudo, valor));
        doc.add(p);
    }
}
