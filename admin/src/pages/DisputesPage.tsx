import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';

interface Dispute {
  id: string;
  serviceRequestId: string;
  abertaPor: string;
  motivo: string;
  valorRetido: number;
  status: string;
  criadaEm: string;
  parteCliente?: { nome: string };
  partePrestador?: { nome: string };
}

export default function DisputesPage() {
  const navigate = useNavigate();
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter] = useState('ABERTA');
  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Dispute[]>(`/admin/disputes?status=${statusFilter}`);
      setDisputes(Array.isArray(data) ? data : []);
    } catch {
      setDisputes([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [statusFilter]);

  function fmt(n: number) {
    return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  function age(s: string) {
    const ms = Date.now() - new Date(s).getTime();
    const h = Math.floor(ms / 3600000);
    if (h < 24) return `${h}h`;
    const d = Math.floor(h / 24);
    return `${d}d ${h % 24}h`;
  }

  const totalRetido = disputes.reduce((s, d) => s + (d.valorRetido || 0), 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: '100vh' }}>
      {/* Top bar */}
      <div style={S.topbar}>
        <span style={S.topTitle}>Fila de disputas</span>
        <button
          style={S.exportBtn}
          onClick={() => navigate('/reports')}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--primary)" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3v12"/><polyline points="7 11 12 16 17 11"/><path d="M4 20h16"/></svg>
          Exportar
        </button>
      </div>

      {/* Content */}
      <div style={S.content}>
        {disputes.length > 0 && (
          <span style={S.summary}>
            {disputes.length} disputa{disputes.length !== 1 ? 's' : ''} em aberto · valor total retido{' '}
            <strong style={{ color: 'var(--text)' }}>{fmt(totalRetido)}</strong>
          </span>
        )}

        {loading ? (
          <div style={S.center}><div className="spinner" /></div>
        ) : disputes.length === 0 ? (
          <div style={S.center}>
            <div style={S.emptyIcon}>
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                <path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"/>
                <path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"/>
                <path d="M7 21H17"/>
                <path d="M12 3v18"/>
                <path d="M3 7h2c2 0 4-1 6-2 2 1 4 2 6 2h2"/>
              </svg>
            </div>
            <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--text)' }}>Nenhuma disputa aberta</div>
            <div style={{ fontSize: 14, color: 'var(--text-soft)' }}>Ótimo! Não há disputas pendentes de mediação.</div>
          </div>
        ) : (
          <div style={S.tableWrap}>
            {/* Header */}
            <div style={S.tableHeader}>
              <span style={S.th}>Pedido</span>
              <span style={S.th}>Partes</span>
              <span style={S.th}>Valor retido</span>
              <span style={S.th}>Idade</span>
              <span />
            </div>
            {/* Rows */}
            {disputes.map(d => {
              const ageStr = age(d.criadaEm);
              const ageH = Math.floor((Date.now() - new Date(d.criadaEm).getTime()) / 3600000);
              const ageColor = ageH > 48 ? 'var(--danger)' : ageH > 24 ? 'var(--warm-terra)' : 'var(--text-soft)';
              const partes = [d.parteCliente?.nome, d.partePrestador?.nome].filter(Boolean).join(' × ') || d.abertaPor;
              return (
                <div key={d.id} style={S.tableRow}>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text)' }}>#{d.serviceRequestId?.slice(-4) || d.id.slice(-4)}</div>
                    <span style={S.disputeBadge}>EM DISPUTA</span>
                  </div>
                  <span style={{ fontSize: 13.5, color: 'var(--text-soft)' }}>{partes}</span>
                  <span style={{ fontSize: 14, fontWeight: 800, color: 'var(--institutional-2)' }}>{fmt(d.valorRetido)}</span>
                  <span style={{ fontSize: 13.5, fontWeight: 700, color: ageColor }}>{ageStr}</span>
                  <span
                    style={S.openLink}
                    onClick={() => navigate(`/disputes/${d.id}`)}
                  >Abrir →</span>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

const S: Record<string, React.CSSProperties> = {
  topbar: {
    height: 64, flexShrink: 0, background: 'var(--surface)',
    borderBottom: '1px solid var(--line-soft)',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '0 28px',
  },
  topTitle: {
    fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)',
  },
  exportBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 8,
    height: 44, padding: '0 18px',
    border: '1.5px solid var(--primary)', borderRadius: 100,
    background: 'var(--surface)', color: 'var(--primary-ink)',
    fontWeight: 700, fontSize: 14, cursor: 'pointer',
  },
  content: {
    flex: 1, overflowY: 'auto' as const,
    background: 'var(--surface-2)', padding: '24px 28px',
    display: 'flex', flexDirection: 'column' as const, gap: 16,
  },
  summary: { fontSize: 13.5, color: 'var(--text-soft)' },
  center: {
    flex: 1, display: 'flex', flexDirection: 'column' as const,
    alignItems: 'center', justifyContent: 'center', gap: 12,
  },
  emptyIcon: { width: 84, height: 84, borderRadius: 26, background: '#E2EEF2', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  tableWrap: {
    background: 'var(--surface)', border: '1px solid var(--line-soft)',
    borderRadius: 12, overflow: 'hidden',
  },
  tableHeader: {
    display: 'grid',
    gridTemplateColumns: '1.2fr 1.8fr 1fr 0.9fr 0.8fr',
    background: 'var(--bg)', padding: '14px 20px',
    borderBottom: '1px solid var(--line-soft)',
  },
  th: {
    fontSize: 12, fontWeight: 700, letterSpacing: '0.1em',
    textTransform: 'uppercase' as const, color: 'var(--institutional-2)',
  },
  tableRow: {
    display: 'grid',
    gridTemplateColumns: '1.2fr 1.8fr 1fr 0.9fr 0.8fr',
    padding: '16px 20px', borderBottom: '1px solid var(--line-soft)',
    alignItems: 'center',
  },
  disputeBadge: {
    display: 'inline-flex', alignItems: 'center', gap: 4,
    marginTop: 4, background: 'var(--terra-tint)', color: 'var(--terra-ink)',
    fontSize: 12, fontWeight: 700, padding: '3px 8px', borderRadius: 100,
  },
  openLink: {
    fontSize: 13.5, fontWeight: 700, color: 'var(--primary)',
    justifySelf: 'end' as const, cursor: 'pointer',
  },
};
