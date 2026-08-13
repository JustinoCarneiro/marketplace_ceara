import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, downloadFile } from '../api/client';

interface OperationalAlert { tipo: string; quantidade: number; }

const ALERT_CONFIG: Record<string, { label: (n: number) => string; bg: string; color: string; route: string }> = {
  SOS_ATIVO: { label: n => `${n} SOS ativo${n !== 1 ? 's' : ''}`, bg: '#FBE6E2', color: '#C0392B', route: '/notifications' },
  DISPUTA_ABERTA: { label: n => `${n} disputa${n !== 1 ? 's' : ''} aberta${n !== 1 ? 's' : ''}`, bg: '#F7E3D6', color: '#A94C25', route: '/disputes' },
  VERIFICACAO_INCONCLUSIVA: { label: n => `${n} verificação${n !== 1 ? 'ões' : ''} pendente${n !== 1 ? 's' : ''}`, bg: '#FDF3D6', color: '#8E6508', route: '/providers' },
};

interface Metrics {
  gmv: number;
  receitaComissao: number;
  ticketMedio: number;
  pedidosPorStatus: Record<string, number>;
  taxaConclusao: number;
  disputasAbertas: number;
  tempoMedioResolucaoHoras: number;
  prestadoresVerificados: number;
  prestadoresAtivos: number;
  clientesAtivos: number;
  sosAcionados: number;
}

const STATUS_LABEL: Record<string, string> = {
  PENDENTE: 'Pendente', ACEITO: 'Aceito',
  EM_ANDAMENTO: 'Andamento', CONCLUIDO: 'Concluído',
  EM_DISPUTA: 'Disputa',
};

const STATUS_COLOR: Record<string, string> = {
  PENDENTE: '#606E71', ACEITO: '#15596E',
  EM_ANDAMENTO: '#10847D', CONCLUIDO: '#1B8C84',
  EM_DISPUTA: '#DA6A32',
};

/** Janelas oferecidas no filtro; null = histórico completo (backend aceita sem datas). */
const PERIODOS = [
  { dias: 7,    label: 'Últimos 7 dias' },
  { dias: 30,   label: 'Últimos 30 dias' },
  { dias: 90,   label: 'Últimos 90 dias' },
  { dias: null, label: 'Todo o período' },
] as const;

export default function DashboardPage() {
  const nav = useNavigate();
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [alerts, setAlerts] = useState<OperationalAlert[]>([]);
  const [loading, setLoading] = useState(true);
  const [dias, setDias] = useState<number | null>(30);
  const [bairro, setBairro] = useState('');
  const [bairros, setBairros] = useState<string[]>([]);
  const [exporting, setExporting] = useState(false);
  const [exportErr, setExportErr] = useState('');

  // US23 (parte 2): "Todos os bairros" era rótulo fixo, sem filtro real por trás (achado em
  // auditoria de docs 2026-08-08). Lista vem dos bairros que já têm pelo menos um pedido —
  // não faz sentido oferecer opção que sempre devolve lista vazia.
  useEffect(() => {
    api.get<string[]>('/admin/bairros').then(setBairros).catch(() => setBairros([]));
  }, []);

  // Independente do período do dashboard — é "o que precisa de mim agora", não histórico.
  useEffect(() => {
    api.get<OperationalAlert[]>('/admin/alerts').then(setAlerts).catch(() => setAlerts([]));
  }, []);

  async function exportar() {
    setExporting(true);
    setExportErr('');
    try {
      const qs = bairro ? `?bairro=${encodeURIComponent(bairro)}` : '';
      await downloadFile(`/admin/reports/metrics.pdf${qs}`, 'metrics.pdf');
    }
    catch (e: unknown) { setExportErr(e instanceof Error ? e.message : 'Erro ao exportar o PDF.'); }
    finally { setExporting(false); }
  }

  useEffect(() => {
    let cancelado = false;
    (async () => {
      setLoading(true);
      try {
        const params = new URLSearchParams();
        if (dias !== null) {
          const inicio = new Date();
          inicio.setDate(inicio.getDate() - dias);
          params.set('de', inicio.toISOString().slice(0, 10));
          params.set('ate', new Date().toISOString().slice(0, 10));
        }
        if (bairro) params.set('bairro', bairro);
        const qs = params.toString();
        const data = await api.get<Metrics>(`/admin/metrics${qs ? `?${qs}` : ''}`);
        if (!cancelado) setMetrics(data);
      } catch {
        if (!cancelado) setMetrics(null);
      } finally {
        if (!cancelado) setLoading(false);
      }
    })();
    return () => { cancelado = true; };
  }, [dias, bairro]);

  const fmt = (n: number | null | undefined) => (n ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const num = (n: number | null | undefined) => (n ?? 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: '100vh' }}>
      {/* Header */}
      <div style={{ height: 64, flexShrink: 0, background: '#FCF8EE', borderBottom: '1px solid #E6DDC9', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: '#0E2A33' }}>Dashboard de métricas</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          {/* Era um rótulo fixo ("Últimos 30 dias · Todos os bairros") sem controle
              nenhum atrás. Agora é um filtro real: mudar recarrega as métricas do
              período no backend (US23). */}
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600, color: '#4C636A', background: '#F3ECDC', border: '1px solid #E6DDC9', padding: '8px 14px', borderRadius: 100 }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg>
            <select
              aria-label="Período das métricas"
              value={String(dias)}
              onChange={e => setDias(e.target.value === 'null' ? null : Number(e.target.value))}
              style={{ border: 'none', background: 'transparent', font: 'inherit', color: 'inherit', cursor: 'pointer', outline: 'none' }}
            >
              {PERIODOS.map(p => <option key={p.label} value={String(p.dias)}>{p.label}</option>)}
            </select>
          </label>
          <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600, color: '#4C636A', background: '#F3ECDC', border: '1px solid #E6DDC9', padding: '8px 14px', borderRadius: 100 }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
            <select
              aria-label="Bairro"
              data-testid="select-bairro"
              value={bairro}
              onChange={e => setBairro(e.target.value)}
              style={{ border: 'none', background: 'transparent', font: 'inherit', color: 'inherit', cursor: 'pointer', outline: 'none' }}
            >
              <option value="">Todos os bairros</option>
              {bairros.map(b => <option key={b} value={b}>{b}</option>)}
            </select>
          </label>
          <button onClick={exportar} disabled={exporting} style={{ display: 'inline-flex', alignItems: 'center', gap: 8, height: 44, padding: '0 20px', border: 'none', borderRadius: 100, background: '#10847D', color: '#fff', fontWeight: 700, fontSize: 14, cursor: exporting ? 'default' : 'pointer', opacity: exporting ? 0.7 : 1, boxShadow: '0 14px 24px -14px rgba(20,168,160,.85)' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3v12"/><polyline points="7 11 12 16 17 11"/><path d="M4 20h16"/></svg>{exporting ? 'Exportando…' : 'Exportar'}
          </button>
          {exportErr && <span style={{ fontSize: 12.5, color: '#C0392B' }}>{exportErr}</span>}
          <div style={{ width: 44, height: 44, borderRadius: 12, background: '#15596E', color: '#fff', fontWeight: 700, fontSize: 14, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>GM</div>
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        {alerts.length > 0 && (
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {alerts.map(a => {
              const cfg = ALERT_CONFIG[a.tipo];
              if (!cfg) return null;
              return (
                <div
                  key={a.tipo}
                  onClick={() => nav(cfg.route)}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: cfg.bg, color: cfg.color, fontSize: 13, fontWeight: 700, padding: '9px 14px', borderRadius: 100, cursor: 'pointer' }}
                >
                  <span style={{ width: 7, height: 7, borderRadius: '50%', background: cfg.color }} />
                  {cfg.label(a.quantidade)}
                </div>
              );
            })}
          </div>
        )}
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> :
         !metrics ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--danger)' }}>Erro ao carregar métricas.</div> : (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
              {/* As variações ("▲ 12% vs. anterior", "▲ 9%", "— estável", "▲ 1,4 pp") eram
                  fixas no código e apareciam como se fossem cálculo real. Removidas: cada
                  card mostra só o que o backend de fato apura. Comparativo com o período
                  anterior entra quando houver a consulta que o produza. */}
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>GMV</span>
                <span data-testid="kpi-gmv" style={S.kpiValue}>{fmt(metrics.gmv)}</span>
                <span style={S.kpiHint}>retido + liberado no período</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Receita comissão</span>
                <span data-testid="kpi-comissao" style={S.kpiValue}>{fmt(metrics.receitaComissao)}</span>
                <span style={S.kpiHint}>comissão já liberada</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Ticket médio</span>
                <span data-testid="kpi-ticket" style={S.kpiValue}>{fmt(metrics.ticketMedio)}</span>
                <span style={S.kpiHint}>por transação do período</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Taxa de conclusão</span>
                <span data-testid="kpi-taxa" style={S.kpiValue}>{(num(metrics.taxaConclusao) * 100).toFixed(1)}%</span>
                <span style={S.kpiHint}>concluídos ÷ pedidos do período</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Disputas abertas</span>
                <span data-testid="kpi-disputas" style={{ ...S.kpiValue, color: '#DA6A32' }}>{num(metrics.disputasAbertas)}</span>
                <span style={S.kpiHint}>
                  {num(metrics.tempoMedioResolucaoHoras) > 0
                    ? `tempo médio ${num(metrics.tempoMedioResolucaoHoras).toFixed(0)} h`
                    : 'sem histórico de mediação'}
                </span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>SOS acionados</span>
                <span data-testid="kpi-sos" style={{ ...S.kpiValue, color: '#C0392B' }}>{num(metrics.sosAcionados)}</span>
                <span style={S.kpiHint}>no período</span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 16 }}>
              <div style={{ flex: 1.6, background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 20, display: 'flex', flexDirection: 'column', gap: 18 }}>
                <span style={{ fontSize: 15, fontWeight: 800, color: '#0E2A33' }}>Pedidos por status</span>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20, height: 150 }}>
                  {/* Antes o gráfico inventava dados quando o campo faltava
                      (`?? 10 : 6 : 4`, total `|| 30`) — desenhava barras convincentes
                      sobre nada. Agora reflete só o que veio da API; zero é zero. */}
                  {['PENDENTE', 'ACEITO', 'EM_ANDAMENTO', 'CONCLUIDO', 'EM_DISPUTA'].map(status => {
                    const count = metrics.pedidosPorStatus?.[status] ?? 0;
                    const total = Object.values(metrics.pedidosPorStatus || {}).reduce((a, b) => a + b, 0);
                    const pct = total > 0 ? (count / total) * 100 : 0;
                    return (
                      <div key={status} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, height: '100%', justifyContent: 'flex-end' }}>
                        <div
                          data-testid={`barra-${status}`}
                          title={`${STATUS_LABEL[status]}: ${count}`}
                          style={{ width: '100%', maxWidth: 54, height: `${count > 0 ? Math.max(pct, 6) : 2}%`, background: count > 0 ? STATUS_COLOR[status] : '#E6DDC9', borderRadius: '8px 8px 0 0' }}
                        />
                        <span style={{ fontSize: 12, color: '#4C636A', fontWeight: 600 }}>{STATUS_LABEL[status]} <span style={{ color: '#606E71' }}>({count})</span></span>
                      </div>
                    );
                  })}
                </div>
              </div>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div style={{ flex: 1, background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <span style={S.kpiTitle}>Prestadores verificados</span>
                  <span data-testid="kpi-prestadores" style={S.kpiValue}>{num(metrics.prestadoresVerificados)} <span style={{ fontSize: 14, color: '#606E71', fontWeight: 600 }}>/ {num(metrics.prestadoresAtivos)} ativos</span></span>
                </div>
                <div style={{ flex: 1, background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <span style={S.kpiTitle}>Clientes ativos</span>
                  <span data-testid="kpi-clientes" style={S.kpiValue}>{num(metrics.clientesAtivos).toLocaleString('pt-BR')}</span>
                </div>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

const S = {
  kpiCard: { background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column' as const, gap: 7 },
  kpiTitle: { fontSize: 12, fontWeight: 700, letterSpacing: '0.12em', textTransform: 'uppercase' as const, color: '#15596E' },
  kpiValue: { fontSize: 26, fontWeight: 800, color: '#0E2A33', letterSpacing: '-0.02em' },
  kpiHint: { fontSize: 12.5, color: '#606E71', fontWeight: 600 },
};
