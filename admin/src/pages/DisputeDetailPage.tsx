import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';

// Contrato real do backend (DisputaDetalheDto): serviceRequestId, categoria, status,
// valorTotal, statusPagamento, motivoDisputa/detalhesDisputa (quem abriu a disputa
// informou na hora), decisao (null até resolvida), criadoEm.
interface DisputeDetail {
  serviceRequestId: string;
  categoria: string;
  status: string;
  valorTotal: number;
  statusPagamento: string;
  motivoDisputa?: string;
  detalhesDisputa?: string;
  decisao?: string;
  criadoEm: string;
}

const S = {
  topbar: { height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', gap: 14, padding: '0 28px' } as React.CSSProperties,
  badge: { display: 'inline-flex', alignItems: 'center', gap: 5, background: '#F7E3D6', color: '#C2572A', fontSize: 12, fontWeight: 800, letterSpacing: '0.05em', padding: '4px 10px', borderRadius: 100 } as React.CSSProperties,
  card: { background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column' as const, gap: 12 } as React.CSSProperties,
  escrow: { background: '#0E3F52', borderRadius: 12, padding: 20, display: 'flex', flexDirection: 'column' as const, gap: 10 } as React.CSSProperties,
  btnG: { width: '100%', height: 50, border: 'none', borderRadius: 12, color: '#fff', fontWeight: 700, fontSize: 14.5, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, cursor: 'pointer' } as React.CSSProperties,
};

export default function DisputeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const nav = useNavigate();
  const [d, setD] = useState<DisputeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    (async () => {
      try { setD(await api.get<DisputeDetail>(`/admin/disputes/${id}`)); }
      catch { setD(null); }
      finally { setLoading(false); }
    })();
  }, [id]);

  // Valores exigidos pelo enum MediationDecision do backend (CONCLUIR|REEMBOLSAR) —
  // NÃO são livres, um valor diferente falha a deserialização no POST.
  async function resolve(decisao: 'CONCLUIR' | 'REEMBOLSAR') {
    setErr(''); setSubmitting(true);
    try { await api.post(`/admin/disputes/${id}/resolve`, { decisao, justificativa: 'Mediação admin' }); setDone(true); }
    catch (e: unknown) { setErr(e instanceof Error ? e.message : 'Erro'); }
    finally { setSubmitting(false); }
  }

  const fmt = (n: number) => n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

  if (loading) return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 1 }}><div className="spinner" /></div>;
  if (!d) return <div style={{ padding: 36, color: 'var(--danger)' }}>Disputa não encontrada.</div>;

  if (done) return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, gap: 12 }}>
      <div style={{ width: 80, height: 80, borderRadius: 26, background: '#DDF0EC', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#15756E" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg>
      </div>
      <span style={{ fontSize: 22, fontWeight: 800, color: 'var(--text)' }}>Disputa resolvida</span>
      <button onClick={() => nav('/disputes')} style={{ height: 44, padding: '0 20px', border: '1.5px solid var(--line)', borderRadius: 100, background: 'transparent', color: 'var(--text-soft)', fontWeight: 700, fontSize: 14, cursor: 'pointer' }}>Voltar</button>
    </div>
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={S.topbar}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#0E2A33" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" onClick={() => nav('/disputes')} style={{ cursor: 'pointer' }}><polyline points="15 5 8 12 15 19"/></svg>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Disputa · Chamado #{d.serviceRequestId.slice(-4)}</span>
        <span style={S.badge}>{d.status.replace('_', ' ')}</span>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', gap: 20 }}>
        <div style={{ flex: 1.5, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={S.card}>
            <span style={{ fontSize: 14, fontWeight: 800, color: 'var(--text)' }}>Pedido</span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}><span style={{ fontSize: 13, color: '#8A989B' }}>Categoria</span><span style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>{d.categoria}</span></div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}><span style={{ fontSize: 13, color: '#8A989B' }}>Status do pagamento</span><span style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>{d.statusPagamento}</span></div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}><span style={{ fontSize: 13, color: '#8A989B' }}>Aberta em</span><span style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>{new Date(d.criadoEm).toLocaleString('pt-BR')}</span></div>
              {d.decisao && <div style={{ display: 'flex', justifyContent: 'space-between' }}><span style={{ fontSize: 13, color: '#8A989B' }}>Decisão</span><span style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>{d.decisao}</span></div>}
            </div>
          </div>
          {(d.motivoDisputa || d.detalhesDisputa) && (
            <div style={S.card}>
              <span style={{ fontSize: 14, fontWeight: 800, color: 'var(--text)' }}>Motivo informado</span>
              {d.motivoDisputa && <span style={S.badge}>{d.motivoDisputa}</span>}
              {d.detalhesDisputa && <span style={{ fontSize: 13.5, color: '#4C636A', lineHeight: 1.5 }}>{d.detalhesDisputa}</span>}
            </div>
          )}
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={S.escrow}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 12, fontWeight: 800, letterSpacing: '0.05em', color: '#fff' }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: '#B7DCE3' }} />VALOR RETIDO</span>
            <span style={{ fontSize: 30, fontWeight: 800, color: '#fff', letterSpacing: '-0.02em' }}>{fmt(d.valorTotal)}</span>
            <span style={{ fontSize: 12.5, color: '#B7DCE3' }}>Decida para quem o valor será liberado.</span>
          </div>
          <div style={S.card}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#0E2A33' }}>Resolver disputa</span>
            <button style={{ ...S.btnG, background: '#1B8C84' }} onClick={() => resolve('CONCLUIR')} disabled={submitting}>
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg>Liberar ao prestador
            </button>
            <button style={{ ...S.btnG, background: '#DA6A32' }} onClick={() => resolve('REEMBOLSAR')} disabled={submitting}>
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12a9 9 0 109-9 9 9 0 00-7 3.3"/><polyline points="3 4 3 8 7 8"/></svg>Reembolsar o cliente
            </button>
            {err && <span style={{ fontSize: 13, color: '#C0392B' }}>{err}</span>}
            <span style={{ fontSize: 12, color: '#8A989B', textAlign: 'center' }}>Ação auditada e irreversível.</span>
          </div>
        </div>
      </div>
    </div>
  );
}
