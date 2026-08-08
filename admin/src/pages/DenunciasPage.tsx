import { useEffect, useState } from 'react';
import { api } from '../api/client';

// Contrato real do backend (DenunciaAdminDto).
interface Denuncia {
  id: string;
  tipo: 'PRESTADOR' | 'AVALIACAO';
  alvoId: string;
  alvoLabel: string;
  denuncianteNome: string;
  motivo: string;
  detalhes: string | null;
  status: string;
  criadoEm: string;
}

export default function DenunciasPage() {
  const [denuncias, setDenuncias] = useState<Denuncia[]>([]);
  const [loading, setLoading] = useState(true);
  const [resolvendo, setResolvendo] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Denuncia[]>('/admin/denuncias');
      setDenuncias(Array.isArray(data) ? data : []);
    } catch {
      setDenuncias([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function resolver(id: string) {
    setResolvendo(id);
    try {
      await api.post(`/admin/denuncias/${id}/resolver`);
      setDenuncias(prev => prev.filter(d => d.id !== id));
    } catch {
      // fica na lista — moderador tenta de novo
    } finally {
      setResolvendo(null);
    }
  }

  function age(s: string) {
    const ms = Date.now() - new Date(s).getTime();
    const h = Math.floor(ms / 3600000);
    if (h < 24) return `${h}h`;
    const d = Math.floor(h / 24);
    return `${d}d ${h % 24}h`;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: '100vh' }}>
      <div style={S.topbar}>
        <span style={S.topTitle}>Fila de denúncias</span>
      </div>

      <div style={S.content}>
        {denuncias.length > 0 && (
          <span style={S.summary}>
            {denuncias.length} denúncia{denuncias.length !== 1 ? 's' : ''} em aberto
          </span>
        )}

        {loading ? (
          <div style={S.center}><div className="spinner" /></div>
        ) : denuncias.length === 0 ? (
          <div style={S.center}>
            <div style={S.emptyIcon}>
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#244C86" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" y1="22" x2="4" y2="15"/>
              </svg>
            </div>
            <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--text)' }}>Nenhuma denúncia aberta</div>
            <div style={{ fontSize: 14, color: 'var(--text-soft)' }}>Prestadores e avaliações reportados aparecem aqui.</div>
          </div>
        ) : (
          <div style={S.tableWrap}>
            <div style={S.tableHeader}>
              <span style={S.th}>Tipo</span>
              <span style={S.th}>Denunciado</span>
              <span style={S.th}>Motivo</span>
              <span style={S.th}>Denunciante</span>
              <span style={S.th}>Idade</span>
              <span />
            </div>
            {denuncias.map(d => (
              <div key={d.id} style={S.tableRow}>
                <span style={d.tipo === 'PRESTADOR' ? S.badgePrestador : S.badgeAvaliacao}>
                  {d.tipo === 'PRESTADOR' ? 'Prestador' : 'Avaliação'}
                </span>
                <div>
                  <div style={{ fontSize: 13.5, fontWeight: 700, color: 'var(--text)' }}>{d.alvoLabel}</div>
                  {d.detalhes && (
                    <div style={{ fontSize: 12.5, color: 'var(--text-soft)', marginTop: 2 }}>{d.detalhes}</div>
                  )}
                </div>
                <span style={{ fontSize: 13.5, color: 'var(--text-soft)' }}>{d.motivo}</span>
                <span style={{ fontSize: 13.5, color: 'var(--text-soft)' }}>{d.denuncianteNome}</span>
                <span style={{ fontSize: 13.5, fontWeight: 700, color: 'var(--text-soft)' }}>{age(d.criadoEm)}</span>
                <button
                  style={S.resolveBtn}
                  disabled={resolvendo === d.id}
                  onClick={() => resolver(d.id)}
                >
                  {resolvendo === d.id ? 'Resolvendo…' : 'Resolver'}
                </button>
              </div>
            ))}
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
  topTitle: { fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' },
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
  emptyIcon: { width: 84, height: 84, borderRadius: 26, background: '#E8EEFA', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  tableWrap: {
    background: 'var(--surface)', border: '1px solid var(--line-soft)',
    borderRadius: 12, overflow: 'hidden',
  },
  tableHeader: {
    display: 'grid',
    gridTemplateColumns: '0.9fr 1.6fr 1.3fr 1.1fr 0.8fr 0.9fr',
    background: 'var(--bg)', padding: '14px 20px',
    borderBottom: '1px solid var(--line-soft)',
  },
  th: {
    fontSize: 12, fontWeight: 700, letterSpacing: '0.1em',
    textTransform: 'uppercase' as const, color: 'var(--institutional-2)',
  },
  tableRow: {
    display: 'grid',
    gridTemplateColumns: '0.9fr 1.6fr 1.3fr 1.1fr 0.8fr 0.9fr',
    padding: '16px 20px', borderBottom: '1px solid var(--line-soft)',
    alignItems: 'center', gap: 8,
  },
  badgePrestador: {
    display: 'inline-flex', alignItems: 'center', justifySelf: 'start',
    background: '#E8EEFA', color: '#244C86',
    fontSize: 12, fontWeight: 700, padding: '3px 10px', borderRadius: 100,
  },
  badgeAvaliacao: {
    display: 'inline-flex', alignItems: 'center', justifySelf: 'start',
    background: 'var(--terra-tint)', color: 'var(--terra-ink)',
    fontSize: 12, fontWeight: 700, padding: '3px 10px', borderRadius: 100,
  },
  resolveBtn: {
    height: 36, padding: '0 16px',
    border: '1.5px solid var(--primary)', borderRadius: 100,
    background: 'var(--surface)', color: 'var(--primary-ink)',
    fontWeight: 700, fontSize: 13, cursor: 'pointer',
    justifySelf: 'end' as const,
  },
};
