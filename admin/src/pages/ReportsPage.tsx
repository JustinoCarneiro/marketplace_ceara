import { useState } from 'react';
import { getToken } from '../store/auth';
import PageHeader from '../components/PageHeader';

const BASE = 'http://localhost:8080/api/v1';

interface ReportConfig {
  id: string;
  icon: string;
  title: string;
  description: string;
  endpoint: string;
  formats: ('csv' | 'pdf')[];
  hasDateRange: boolean;
}

const REPORTS: ReportConfig[] = [
  {
    id: 'metrics',
    icon: '📊',
    title: 'Métricas da plataforma',
    description: 'GMV, receita, ticket médio, taxas e contagens por status.',
    endpoint: '/admin/reports/metrics',
    formats: ['pdf'],
    hasDateRange: true,
  },
  {
    id: 'transactions',
    icon: '💳',
    title: 'Transações financeiras',
    description: 'Histórico completo de transações de escrow com status de pagamento.',
    endpoint: '/admin/reports/transactions',
    formats: ['csv'],
    hasDateRange: true,
  },
  {
    id: 'disputes',
    icon: '⚖️',
    title: 'Disputas',
    description: 'Lista de disputas com motivos, decisões e valores envolvidos.',
    endpoint: '/admin/reports/disputes',
    formats: ['csv'],
    hasDateRange: false,
  },
  {
    id: 'requests',
    icon: '📋',
    title: 'Pedidos de serviço',
    description: 'Pedidos por status, categoria e período.',
    endpoint: '/admin/reports/requests',
    formats: ['csv'],
    hasDateRange: true,
  },
];

export default function ReportsPage() {
  const [dateFrom, setDateFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 30);
    return d.toISOString().slice(0, 10);
  });
  const [dateTo, setDateTo] = useState(() => new Date().toISOString().slice(0, 10));
  const [downloading, setDownloading] = useState<string | null>(null);
  const [msg, setMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  async function download(report: ReportConfig, format: 'csv' | 'pdf') {
    const key = `${report.id}-${format}`;
    setDownloading(key);
    setMsg(null);
    try {
      const params = new URLSearchParams();
      if (report.hasDateRange) {
        params.set('de', dateFrom);
        params.set('ate', dateTo);
      }

      let url: string;
      if (format === 'pdf') {
        url = `${BASE}${report.endpoint}.pdf?${params}`;
      } else {
        url = `${BASE}${report.endpoint}.csv?${params}`;
      }

      const res = await fetch(url, {
        headers: { Authorization: `Bearer ${getToken()}` },
      });

      if (!res.ok) throw new Error(`Falha ao gerar relatório: ${res.status}`);

      const blob = await res.blob();
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `onda-${report.id}-${dateFrom}-${dateTo}.${format}`;
      link.click();
      URL.revokeObjectURL(link.href);
      setMsg({ type: 'ok', text: `Relatório "${report.title}" baixado com sucesso.` });
    } catch (e: unknown) {
      setMsg({ type: 'err', text: e instanceof Error ? e.message : 'Erro ao baixar.' });
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 900 }}>
      <PageHeader
        title="Exportar Relatórios"
        subtitle="Extração de dados para análise externa e prestação de contas"
      />

      {/* Date range */}
      <div className="card" style={{ padding: 20, marginBottom: 24 }}>
        <p className="eyebrow" style={{ marginBottom: 14 }}>Período padrão (para relatórios com intervalo de datas)</p>
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            type="date" className="input-field" value={dateFrom}
            onChange={e => setDateFrom(e.target.value)}
            style={{ width: 160 }}
          />
          <span style={{ color: 'var(--text-faint)' }}>até</span>
          <input
            type="date" className="input-field" value={dateTo}
            onChange={e => setDateTo(e.target.value)}
            style={{ width: 160 }}
          />
        </div>
      </div>

      {msg && (
        <div className={`alert alert--${msg.type === 'ok' ? 'success' : 'danger'}`} style={{ marginBottom: 20 }}>
          <span>{msg.type === 'ok' ? '✅' : '⚠️'}</span>
          <span>{msg.text}</span>
          <button onClick={() => setMsg(null)} style={{ marginLeft: 'auto', color: 'inherit', fontSize: 18 }}>×</button>
        </div>
      )}

      {/* Report cards */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {REPORTS.map(report => (
          <div key={report.id} className="card" style={{ padding: '20px 24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
              <div style={{
                width: 48, height: 48, borderRadius: 14,
                background: 'var(--sky-tint)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 24, flexShrink: 0,
              }}>
                {report.icon}
              </div>

              <div style={{ flex: 1, minWidth: 200 }}>
                <div style={{ fontWeight: 700, fontSize: 'var(--fs-h3)' }}>{report.title}</div>
                <div style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-soft)', marginTop: 4 }}>
                  {report.description}
                </div>
                {!report.hasDateRange && (
                  <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)', marginTop: 4, display: 'block' }}>
                    Exporta todos os registros (sem filtro de período)
                  </span>
                )}
              </div>

              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                {report.formats.map(fmt => (
                  <button
                    key={fmt}
                    className="btn btn--outline btn--sm"
                    onClick={() => download(report, fmt)}
                    disabled={downloading === `${report.id}-${fmt}`}
                    style={{
                      minWidth: 90,
                      gap: 6,
                    }}
                  >
                    {downloading === `${report.id}-${fmt}` ? (
                      'Gerando…'
                    ) : (
                      <>
                        <span>{fmt === 'pdf' ? '📄' : '📊'}</span>
                        {fmt.toUpperCase()}
                      </>
                    )}
                  </button>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Info */}
      <div className="alert alert--info" style={{ marginTop: 24 }}>
        <span>ℹ️</span>
        <div style={{ fontSize: 'var(--fs-body-sm)' }}>
          <div><strong>CSV</strong> — Compatível com Excel, Google Sheets e ferramentas de BI.</div>
          <div style={{ marginTop: 4 }}>
            <strong>PDF</strong> — Relatório formatado para apresentação e prestação de contas.
          </div>
        </div>
      </div>
    </div>
  );
}
