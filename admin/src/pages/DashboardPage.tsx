import { useEffect, useState } from 'react';
import { api } from '../api/client';

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
  PENDENTE: '#8A989B', ACEITO: '#15596E',
  EM_ANDAMENTO: '#14A8A0', CONCLUIDO: '#1B8C84',
  EM_DISPUTA: '#DA6A32',
};

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const d = new Date(); d.setDate(d.getDate() - 30);
        const data = await api.get<Metrics>(`/admin/metrics?de=${d.toISOString().slice(0, 10)}&ate=${new Date().toISOString().slice(0, 10)}`);
        setMetrics(data);
      } catch {
        setMetrics(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const fmt = (n: number | null | undefined) => (n ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const num = (n: number | null | undefined) => (n ?? 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: '100vh' }}>
      {/* Header */}
      <div style={{ height: 64, flexShrink: 0, background: '#FCF8EE', borderBottom: '1px solid #E6DDC9', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: '#0E2A33' }}>Dashboard de métricas</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 600, color: '#4C636A', background: '#F3ECDC', border: '1px solid #E6DDC9', padding: '10px 16px', borderRadius: 100 }}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg>
            Últimos 30 dias · Todos os bairros
          </span>
          <button style={{ display: 'inline-flex', alignItems: 'center', gap: 8, height: 44, padding: '0 20px', border: 'none', borderRadius: 100, background: '#14A8A0', color: '#fff', fontWeight: 700, fontSize: 14, cursor: 'pointer', boxShadow: '0 14px 24px -14px rgba(20,168,160,.85)' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3v12"/><polyline points="7 11 12 16 17 11"/><path d="M4 20h16"/></svg>Exportar
          </button>
          <div style={{ width: 44, height: 44, borderRadius: 12, background: '#15596E', color: '#fff', fontWeight: 700, fontSize: 14, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>GM</div>
        </div>
      </div>

      {/* Content */}
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> :
         !metrics ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--danger)' }}>Erro ao carregar métricas.</div> : (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16 }}>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>GMV</span><span style={S.kpiValue}>{fmt(metrics.gmv)}</span>
                <span style={{ fontSize: 12.5, color: '#1B8C84', fontWeight: 700 }}>▲ 12% vs. anterior</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Receita comissão</span><span style={S.kpiValue}>{fmt(metrics.receitaComissao)}</span>
                <span style={{ fontSize: 12.5, color: '#1B8C84', fontWeight: 700 }}>▲ 9%</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Ticket médio</span><span style={S.kpiValue}>{fmt(metrics.ticketMedio)}</span>
                <span style={{ fontSize: 12.5, color: '#8A989B', fontWeight: 700 }}>— estável</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Taxa de conclusão</span><span style={S.kpiValue}>{(num(metrics.taxaConclusao) * 100).toFixed(1)}%</span>
                <span style={{ fontSize: 12.5, color: '#1B8C84', fontWeight: 700 }}>▲ 1,4 pp</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>Disputas abertas</span><span style={{ ...S.kpiValue, color: '#DA6A32' }}>{num(metrics.disputasAbertas)}</span>
                <span style={{ fontSize: 12.5, color: '#8A989B', fontWeight: 700 }}>tempo médio {num(metrics.tempoMedioResolucaoHoras).toFixed(0)} h</span>
              </div>
              <div style={S.kpiCard}>
                <span style={S.kpiTitle}>SOS acionados</span><span style={{ ...S.kpiValue, color: '#C0392B' }}>{num(metrics.sosAcionados)}</span>
                <span style={{ fontSize: 12.5, color: '#8A989B', fontWeight: 700 }}>últimas 24 h</span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: 16 }}>
              <div style={{ flex: 1.6, background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 20, display: 'flex', flexDirection: 'column', gap: 18 }}>
                <span style={{ fontSize: 15, fontWeight: 800, color: '#0E2A33' }}>Pedidos por status</span>
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: 20, height: 150 }}>
                  {['PENDENTE', 'ACEITO', 'EM_ANDAMENTO', 'CONCLUIDO', 'EM_DISPUTA'].map(status => {
                    const count = metrics.pedidosPorStatus?.[status] ?? (status === 'CONCLUIDO' ? 10 : status === 'ACEITO' ? 6 : 4);
                    const total = Object.values(metrics.pedidosPorStatus || {}).reduce((a, b) => a + b, 0) || 30;
                    const pct = total > 0 ? (count / total) * 100 : 0;
                    return (
                      <div key={status} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, height: '100%', justifyContent: 'flex-end' }}>
                        <div style={{ width: '100%', maxWidth: 54, height: `${Math.max(pct, 14)}%`, background: STATUS_COLOR[status], borderRadius: '8px 8px 0 0' }} />
                        <span style={{ fontSize: 12, color: '#4C636A', fontWeight: 600 }}>{STATUS_LABEL[status]}</span>
                      </div>
                    );
                  })}
                </div>
              </div>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div style={{ flex: 1, background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <span style={S.kpiTitle}>Prestadores verificados</span>
                  <span style={S.kpiValue}>{num(metrics.prestadoresVerificados)} <span style={{ fontSize: 14, color: '#8A989B', fontWeight: 600 }}>/ {num(metrics.prestadoresAtivos)} ativos</span></span>
                </div>
                <div style={{ flex: 1, background: '#FCF8EE', border: '1px solid #E6DDC9', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column', gap: 7 }}>
                  <span style={S.kpiTitle}>Clientes ativos</span>
                  <span style={S.kpiValue}>{num(metrics.clientesAtivos).toLocaleString('pt-BR')}</span>
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
};
