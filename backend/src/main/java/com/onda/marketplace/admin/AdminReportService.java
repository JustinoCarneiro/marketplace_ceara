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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    /**
     * Fuso do negócio (marketplace hiperlocal do Ceará). O dia do filtro é o dia de quem opera
     * o painel, não o dia UTC: convertendo em UTC, tudo que acontecia entre 21h e a meia-noite
     * local já contava como o dia seguinte e sumia do "hoje". Fortaleza não tem horário de verão.
     */
    static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Fortaleza");

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Início do dia informado no fuso do negócio; {@code null} continua {@code null} (sem filtro). */
    static Instant inicioDoDia(LocalDate dia) {
        return dia != null ? dia.atStartOfDay(ZONA_NEGOCIO).toInstant() : null;
    }

    /**
     * Fim EXCLUSIVO do período: o dia informado entra inteiro, então o limite é o começo do dia
     * seguinte — senão o próprio dia escolhido ficaria de fora.
     */
    static Instant fimExclusivoDoDia(LocalDate dia) {
        return dia != null ? dia.plusDays(1).atStartOfDay(ZONA_NEGOCIO).toInstant() : null;
    }

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
        return metrics(null, null, null);
    }

    /**
     * Métricas do dashboard (US23) com filtro de período opcional.
     * O recorte vale para as métricas de fluxo; as de estoque descrevem o estado
     * atual e ignoram as datas — ver {@link MetricsDto}.
     *
     * @param de     início do período (inclusive), ou null para "desde sempre"
     * @param ate    fim do período (exclusive), ou null para "até agora"
     * @param bairro filtro opcional (US23 parte 2) — recorta {@code pedidosPorStatus} e
     *               derivados (totalPedidos, taxaConclusao). GMV/comissão/disputas/SOS
     *               continuam agregados por toda a base: são estoque de negócio, não de
     *               volume de pedidos, e escopar por bairro exigiria join em mais três
     *               repositórios pra um recorte que a tela de métricas ainda não pede.
     */
    @Transactional(readOnly = true)
    public MetricsDto metrics(Instant deOuNull, Instant ateOuNull, String bairro) {
        Instant de  = deOuNull  != null ? deOuNull  : INICIO_DOS_TEMPOS;
        Instant ate = ateOuNull != null ? ateOuNull : FIM_DOS_TEMPOS;

        boolean temBairro = bairro != null && !bairro.isBlank();
        List<Object[]> porStatus = temBairro
                ? srRepository.contarPorStatusNoPeriodoEBairro(de, ate, bairro)
                : srRepository.contarPorStatusNoPeriodo(de, ate);

        Map<String, Long> pedidosPorStatus = new LinkedHashMap<>();
        for (Object[] linha : porStatus) {
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
    public List<String> bairrosDisponiveis() {
        return srRepository.bairrosDistintos();
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

    /**
     * Exporta um recurso em CSV respeitando os filtros da tela (US29): o recorte vale pela
     * data de criação ({@code criadoEm}, a coluna que o próprio arquivo mostra) e pelo bairro
     * do pedido — na transação, via o pedido dela.
     *
     * @param de     início do período (inclusive), ou null para "desde sempre"
     * @param ate    fim do período (exclusive), ou null para "até agora"
     * @param bairro filtro opcional; em branco não filtra
     */
    @Transactional(readOnly = true)
    public String exportarCsv(String recurso, Instant deOuNull, Instant ateOuNull, String bairro) {
        Instant de  = deOuNull  != null ? deOuNull  : INICIO_DOS_TEMPOS;
        Instant ate = ateOuNull != null ? ateOuNull : FIM_DOS_TEMPOS;
        return switch (recurso) {
            case "transactions" -> exportarTransacoes(de, ate, bairro);
            case "requests"     -> exportarPedidos(de, ate, bairro);
            default -> throw new BusinessException("UNKNOWN_REPORT",
                    "Relatório desconhecido: " + recurso);
        };
    }

    private String exportarTransacoes(Instant de, Instant ate, String bairro) {
        boolean temBairro = bairro != null && !bairro.isBlank();
        List<Transaction> transacoes = temBairro
                ? transactionRepository.listarNoPeriodoEBairro(de, ate, bairro)
                : transactionRepository.listarNoPeriodo(de, ate);
        StringBuilder sb = new StringBuilder(
                "id,serviceRequestId,valorTotal,valorComissao,metodo,statusPagamento,criadoEm");
        for (Transaction t : transacoes) {
            sb.append('\n').append(linha(
                    t.getId(), t.getServiceRequestId(), t.getValorTotal(), t.getValorComissao(),
                    t.getMetodo(), t.getStatusPagamento(), t.getCreatedAt()));
        }
        return sb.toString();
    }

    private String exportarPedidos(Instant de, Instant ate, String bairro) {
        boolean temBairro = bairro != null && !bairro.isBlank();
        List<ServiceRequest> pedidos = temBairro
                ? srRepository.listarNoPeriodoEBairro(de, ate, bairro)
                : srRepository.listarNoPeriodo(de, ate);
        StringBuilder sb = new StringBuilder("id,categoria,bairro,status,criadoEm");
        for (ServiceRequest s : pedidos) {
            sb.append('\n').append(linha(
                    s.getId(), s.getCategoria(), s.getBairro() != null ? s.getBairro() : "",
                    s.getStatus(), s.getCreatedAt()));
        }
        return sb.toString();
    }

    private String linha(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(campoCsv(campos[i]));
        }
        return sb.toString();
    }

    /**
     * CSV Formula Injection (CWE-1236): campo de texto livre digitado por usuário (ex.:
     * {@code bairro}, sem validação de conteúdo) começando com {@code = + - @} vira fórmula
     * executável ao abrir no Excel/LibreOffice — prefixo de aspa simples força texto. Também
     * escapa vírgula/aspas/quebra de linha (RFC 4180), senão um bairro com vírgula quebra o
     * alinhamento das colunas do relatório.
     */
    private static String campoCsv(Object valor) {
        String s = String.valueOf(valor);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * Gera PDF em memória com resumo de métricas do painel (US29).
     * NUNCA expõe CPF — somente agregados (TS04/LGPD).
     *
     * @param de     início do período (inclusive), ou null para "desde sempre"
     * @param ate    fim do período (exclusive), ou null para "até agora"
     * @param bairro filtro opcional (US23 parte 2) — mesmo recorte de {@link #metrics}:
     *               só pedidosPorStatus/totalPedidos/taxaConclusao respeitam o bairro.
     * @return array de bytes do PDF
     */
    @Transactional(readOnly = true)
    public byte[] exportarMetricasPdf(Instant de, Instant ate, String bairro) {
        MetricsDto m = metrics(de, ate, bairro);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // Título
            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            doc.add(new Paragraph("Marketplace Ceará — Relatório de Métricas", tituloFont));
            doc.add(new Paragraph("Gerado em: " + java.time.Instant.now()));
            // O arquivo diz com que recorte foi gerado: sem isso, um PDF de "últimos 7 dias"
            // e um de "todo o histórico" são indistinguíveis depois de baixados.
            doc.add(new Paragraph("Período: " + descricaoDoPeriodo(de, ate)));
            if (bairro != null && !bairro.isBlank()) {
                doc.add(new Paragraph("Bairro: " + bairro));
                doc.add(new Paragraph("Obs.: só os números de pedidos (total, concluídos e taxa de "
                        + "conclusão) respeitam o bairro; as demais métricas não são filtradas por bairro."));
            }
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

    /**
     * Texto do período no cabeçalho do PDF. O fim é exclusivo (começo do dia seguinte), então
     * volta um instante pra mostrar o último dia que de fato entrou no relatório.
     */
    private static String descricaoDoPeriodo(Instant de, Instant ate) {
        String inicio = de  != null ? DATA_BR.format(de.atZone(ZONA_NEGOCIO)) : null;
        String fim    = ate != null ? DATA_BR.format(ate.minusMillis(1).atZone(ZONA_NEGOCIO)) : null;
        if (inicio != null && fim != null) return inicio + " a " + fim;
        if (inicio != null)                return "a partir de " + inicio;
        if (fim != null)                   return "até " + fim;
        return "todo o histórico";
    }

    private void adicionarLinha(Document doc, Font label, Font valor,
                                 String chave, String conteudo) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(chave + ": ", label));
        p.add(new Chunk(conteudo, valor));
        doc.add(p);
    }
}
