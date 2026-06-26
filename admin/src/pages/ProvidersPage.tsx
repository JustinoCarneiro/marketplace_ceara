import { useEffect, useState } from 'react';
import { api } from '../api/client';

interface Provider {
  id: string;
  nome: string;
  categoria: string;
  statusVerificacao: string;
  notaMedia?: number;
  totalPedidos?: number;
}

const AVATAR_COLORS = ['#15596E', '#3C7A4E', '#DA6A32', '#C0392B', '#1B8C84', '#244C86'];
function avatarBg(nome: string) { return AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length]; }
function initials(nome: string) { return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase(); }

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { bg: string; color: string; label: string }> = {
    INCONCLUSIVO: { bg: '#F2B015', color: '#0E2A33', label: 'INCONCLUSIVO' },
    VERIFICADO: { bg: '#0E3F52', color: '#fff', label: 'VERIFICADO' },
    REPROVADO: { bg: '#FBE6E2', color: '#C0392B', label: 'REPROVADO' },
    PENDENTE: { bg: '#FDF3D6', color: '#B5810A', label: 'PENDENTE' },
  };
  const s = map[status] || map.PENDENTE;
  return <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: s.bg, color: s.color, fontSize: 12, fontWeight: 800, letterSpacing: '0.05em', padding: '5px 11px', borderRadius: 100, flexShrink: 0 }}>{s.label}</span>;
}

export default function ProvidersPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'INCONCLUSIVO' | ''>('INCONCLUSIVO');

  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Provider[]>('/admin/providers');
      setProviders(Array.isArray(data) ? data : []);
    } catch { setProviders([]); }
    finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function verify(id: string) {
    try { await api.post(`/admin/providers/${id}/verify`, {}); load(); } catch {}
  }
  async function reject(id: string) {
    try { await api.post(`/admin/providers/${id}/reject`, {}); load(); } catch {}
  }

  const filtered = filter ? providers.filter(p => p.statusVerificacao === filter) : providers;
  const pendingCount = providers.filter(p => ['INCONCLUSIVO', 'PENDENTE'].includes(p.statusVerificacao)).length;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Moderação de prestadores</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span onClick={() => setFilter('INCONCLUSIVO')} style={{ fontSize: 12.5, fontWeight: 700, color: filter === 'INCONCLUSIVO' ? '#0E2A33' : '#4C636A', background: filter === 'INCONCLUSIVO' ? '#FDF3D6' : '#F3ECDC', border: `1px solid ${filter === 'INCONCLUSIVO' ? '#F2B015' : '#E6DDC9'}`, padding: '8px 14px', borderRadius: 100, cursor: 'pointer' }}>Inconclusivos · {pendingCount}</span>
          <span onClick={() => setFilter('')} style={{ fontSize: 12.5, fontWeight: 600, color: '#4C636A', background: '#F3ECDC', border: '1px solid #E6DDC9', padding: '8px 14px', borderRadius: 100, cursor: 'pointer' }}>Todos</span>
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 1 }}><div className="spinner" /></div> :
         filtered.length === 0 ? <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-soft)' }}>Nenhum prestador encontrado.</div> :
         filtered.map(p => {
          const isPending = ['INCONCLUSIVO', 'PENDENTE'].includes(p.statusVerificacao);
          const cardBg = isPending ? '#FDF3D6' : 'var(--surface)';
          const cardBorder = isPending ? '1.5px solid #F2B015' : '1px solid var(--line-soft)';
          const opacity = p.statusVerificacao === 'REPROVADO' ? 0.7 : 1;
          return (
            <div key={p.id} style={{ background: cardBg, border: cardBorder, borderRadius: 12, padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 16, opacity }}>
              <div style={{ width: 48, height: 48, borderRadius: 12, background: avatarBg(p.nome), color: '#fff', fontWeight: 800, fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{initials(p.nome)}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 16, fontWeight: 700, color: '#0E2A33' }}>{p.nome}</div>
                <div style={{ fontSize: 13, color: '#4C636A' }}>{p.categoria}</div>
              </div>
              <StatusBadge status={p.statusVerificacao} />
              {isPending ? (
                <>
                  <button onClick={() => verify(p.id)} style={{ height: 42, padding: '0 18px', border: 'none', borderRadius: 100, background: '#14A8A0', color: '#fff', fontWeight: 700, fontSize: 13.5, cursor: 'pointer', flexShrink: 0 }}>Verificar</button>
                  <button onClick={() => reject(p.id)} style={{ height: 42, padding: '0 18px', border: '1.5px solid #C0392B', borderRadius: 100, background: 'transparent', color: '#C0392B', fontWeight: 700, fontSize: 13.5, cursor: 'pointer', flexShrink: 0 }}>Reprovar</button>
                </>
              ) : (
                <span style={{ fontSize: 13.5, fontWeight: 700, color: '#8A989B', flexShrink: 0 }}>Ver perfil →</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
