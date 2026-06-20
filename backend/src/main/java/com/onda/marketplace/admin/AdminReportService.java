package com.onda.marketplace.admin;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Métricas, alertas operacionais e exportação de relatórios do painel admin
 * (US23/US29/US30). Tudo derivado por agregação — sem tabela de verdade
 * financeira (TS09). Relatórios NUNCA expõem CPF (TS04/LGPD).
 */
@Service
public class AdminReportService {

    private final ServiceRequestRepository    srRepository;
    private final TransactionRepository       transactionRepository;
    private final ProviderProfileRepository   providerProfileRepository;
    private final SosAlertRepository          sosRepository;

    public AdminReportService(ServiceRequestRepository srRepository,
                              TransactionRepository transactionRepository,
                              ProviderProfileRepository providerProfileRepository,
                              SosAlertRepository sosRepository) {
        this.srRepository              = srRepository;
        this.transactionRepository     = transactionRepository;
        this.providerProfileRepository = providerProfileRepository;
        this.sosRepository             = sosRepository;
    }

    @Transactional(readOnly = true)
    public MetricsDto metrics() {
        return new MetricsDto(
                srRepository.count(),
                srRepository.countByStatus(ServiceRequestStatus.CONCLUIDO),
                srRepository.countByStatus(ServiceRequestStatus.EM_DISPUTA),
                providerProfileRepository.countByStatusVerificacao(ProviderStatus.VERIFICADO),
                providerProfileRepository.countByStatusVerificacao(ProviderStatus.EM_VERIFICACAO),
                transactionRepository.somaComissaoPorStatus(TransactionStatus.LIBERADO));
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

            adicionarLinha(doc, labelFont, valorFont, "Total de pedidos",           String.valueOf(m.totalPedidos()));
            adicionarLinha(doc, labelFont, valorFont, "Pedidos concluídos",         String.valueOf(m.pedidosConcluidos()));
            adicionarLinha(doc, labelFont, valorFont, "Pedidos em disputa",          String.valueOf(m.pedidosEmDisputa()));
            adicionarLinha(doc, labelFont, valorFont, "Prestadores verificados",     String.valueOf(m.prestadoresVerificados()));
            adicionarLinha(doc, labelFont, valorFont, "Prestadores em verificação", String.valueOf(m.prestadoresEmVerificacao()));
            adicionarLinha(doc, labelFont, valorFont, "Receita de comissão (R$)",   m.receitaComissao().toPlainString());

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
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
