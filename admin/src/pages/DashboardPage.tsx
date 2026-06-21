import { useEffect, useState } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

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
  PENDENTE: 'Pendente', PROPOSTO: 'Proposto', ACEITO: 'Aceito',
  EM_ANDAMENTO: 'Em andamento', CONCLUIDO: 'Concluído',
  EM_DISPUTA: 'Em disputa', CANCELADO: 'Cancelado',
};

function StatCard({
  icon, label, value, sub, highlight,
}: {
  icon: string; label: string; value: string; sub?: string; highlight?: boolean;
}) {
  return (
    <div className="card" style={{
      padding: '20px 24px',
      display: 'flex', flexDirection: 'column', gap: 6,
      borderLeft: highlight ? '4px solid var(--primary)' : '1px solid var(--line-soft)',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ fontSize: 22 }}>{icon}</span>
        <span className="eyebrow">{label}</span>
      </div>
      <div style={{ fontSize: 28, fontWeight: 800, color: 'var(--text)', letterSpacing: '-0.02em' }}>
        {value}
      </div>
      {sub && <div style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>{sub}</div>}
    </div>
  );
}

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [dateFrom, setDateFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 30);
    return d.toISOString().slice(0, 10);
  });
  const [dateTo, setDateTo] = useState(() => new Date().toISOString().slice(0, 10));

  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Metrics>(`/admin/metrics?de=${dateFrom}&ate=${dateTo}`);
      setMetrics(data);
    } catch {
      setMetrics(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [dateFrom, dateTo]);

  function fmt(n: number | null | undefined) {
    return (n ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  function num(n: number | null | undefined) {
    return (n ?? 0);
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 1200 }}>
      <PageHeader
        title="Dashboard"
        subtitle="Visão geral da saúde da plataforma"
        action={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <input
              type="date" className="input-field" value={dateFrom}
              onChange={e => setDateFrom(e.target.value)}
              style={{ width: 150, height: 36 }}
            />
            <span style={{ color: 'var(--text-faint)', fontSize: 14 }}>até</span>
            <input
              type="date" className="input-field" value={dateTo}
              onChange={e => setDateTo(e.target.value)}
              style={{ width: 150, height: 36 }}
            />
          </div>
        }
      />

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : !metrics ? (
        <div className="alert alert--danger">
          <span>⚠️</span>
          <span>Falha ao carregar métricas. Verifique a conexão com o backend.</span>
        </div>
      ) : (
        <>
          {/* Financial KPIs */}
          <div style={{ marginBottom: 8 }}>
            <p className="eyebrow" style={{ marginBottom: 12 }}>Financeiro</p>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
              gap: 16,
            }}>
              <StatCard icon="💰" label="GMV" value={fmt(metrics.gmv)} highlight />
              <StatCard icon="📈" label="Receita comissão" value={fmt(metrics.receitaComissao)} />
              <StatCard icon="🎯" label="Ticket médio" value={fmt(metrics.ticketMedio)} />
              <StatCard
                icon="✅" label="Taxa conclusão"
                value={`${(num(metrics.taxaConclusao) * 100).toFixed(1)}%`}
              />
            </div>
          </div>

          {/* Operations */}
          <div style={{ marginBottom: 8, marginTop: 24 }}>
            <p className="eyebrow" style={{ marginBottom: 12 }}>Operações</p>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
              gap: 16,
            }}>
              <StatCard
                icon="⚖️" label="Disputas abertas"
                value={String(num(metrics.disputasAbertas))}
                sub={num(metrics.disputasAbertas) > 0 ? 'Requerem atenção' : 'Tudo certo'}
                highlight={num(metrics.disputasAbertas) > 0}
              />
              <StatCard
                icon="⏱️" label="Tempo médio resolução"
                value={`${num(metrics.tempoMedioResolucaoHoras).toFixed(1)}h`}
              />
              <StatCard icon="🆘" label="SOS acionados" value={String(num(metrics.sosAcionados))} />
            </div>
          </div>

          {/* Users */}
          <div style={{ marginBottom: 8, marginTop: 24 }}>
            <p className="eyebrow" style={{ marginBottom: 12 }}>Usuários</p>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
              gap: 16,
            }}>
              <StatCard icon="👤" label="Clientes ativos" value={String(num(metrics.clientesAtivos))} />
              <StatCard icon="👷" label="Prestadores ativos" value={String(num(metrics.prestadoresAtivos))} />
              <StatCard
                icon="🛡️" label="Prestadores verificados"
                value={String(num(metrics.prestadoresVerificados))}
              />
            </div>
          </div>

          {/* Requests by status */}
          <div style={{ marginTop: 24 }}>
            <p className="eyebrow" style={{ marginBottom: 12 }}>Pedidos por status</p>
            <div className="card" style={{ overflowX: 'auto' }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th>Quantidade</th>
                    <th style={{ width: '50%' }}>Distribuição</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(metrics.pedidosPorStatus ?? {}).map(([status, count]) => {
                    const total = Object.values(metrics.pedidosPorStatus ?? {}).reduce((a, b) => a + b, 0);
                    const pct = total > 0 ? (count / total) * 100 : 0;
                    return (
                      <tr key={status}>
                        <td>
                          <span className={`badge badge--${status.toLowerCase().replace('_', '')}`}>
                            {STATUS_LABEL[status] ?? status}
                          </span>
                        </td>
                        <td style={{ fontWeight: 700, fontSize: 'var(--fs-h3)' }}>{count}</td>
                        <td>
                          <div style={{
                            height: 8,
                            background: 'var(--line-soft)',
                            borderRadius: 100,
                            overflow: 'hidden',
                          }}>
                            <div style={{
                              height: '100%',
                              width: `${pct}%`,
                              background: 'var(--primary)',
                              borderRadius: 100,
                              transition: 'width 0.5s var(--ease)',
                            }} />
                          </div>
                          <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>
                            {pct.toFixed(1)}%
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
