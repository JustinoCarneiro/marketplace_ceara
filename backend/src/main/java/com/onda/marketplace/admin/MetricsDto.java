package com.onda.marketplace.admin;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Métricas do dashboard administrativo (US23), derivadas por agregação sobre
 * service_requests, transactions, providers_profile, users e sos_alerts — sem
 * tabela própria de verdade financeira (TS09).
 *
 * <p>Duas naturezas de métrica convivem aqui, e o filtro de período só se aplica
 * a uma delas:
 * <ul>
 *   <li><b>Fluxo</b> (respeita {@code de}/{@code ate}): o que aconteceu na janela —
 *       {@code gmv}, {@code receitaComissao}, {@code ticketMedio},
 *       {@code pedidosPorStatus}, {@code taxaConclusao}, {@code sosAcionados},
 *       {@code tempoMedioResolucaoHoras}.</li>
 *   <li><b>Estoque</b> (sempre "agora", ignora o período): o estado atual da
 *       plataforma — {@code disputasAbertas}, {@code prestadoresVerificados},
 *       {@code prestadoresAtivos}, {@code clientesAtivos}. Filtrar "disputas abertas
 *       hoje" por data de criação do pedido esconderia disputa antiga ainda aberta,
 *       que é justamente a que o operador precisa ver.</li>
 * </ul>
 *
 * @param gmv                      volume transacionado: soma dos valores que entraram
 *                                 no escrow e não foram devolvidos (RETIDO + LIBERADO)
 * @param receitaComissao          comissão efetivamente realizada (só LIBERADO)
 * @param ticketMedio              gmv ÷ nº de transações que compõem o gmv
 * @param pedidosPorStatus         contagem de pedidos por status no período
 * @param taxaConclusao            concluídos ÷ total de pedidos do período (0..1)
 * @param disputasAbertas          pedidos EM_DISPUTA agora
 * @param tempoMedioResolucaoHoras média de horas entre abrir e mediar a disputa
 * @param sosAcionados             acionamentos de SOS no período
 */
public record MetricsDto(
        BigDecimal        gmv,
        BigDecimal        receitaComissao,
        BigDecimal        ticketMedio,
        Map<String, Long> pedidosPorStatus,
        double            taxaConclusao,
        long              disputasAbertas,
        double            tempoMedioResolucaoHoras,
        long              prestadoresVerificados,
        long              prestadoresAtivos,
        long              clientesAtivos,
        long              sosAcionados
) {
    /** Total de pedidos do período — derivado de {@link #pedidosPorStatus}. */
    public long totalPedidos() {
        return pedidosPorStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Pedidos concluídos no período — derivado de {@link #pedidosPorStatus}. */
    public long pedidosConcluidos() {
        return pedidosPorStatus.getOrDefault("CONCLUIDO", 0L);
    }
}
