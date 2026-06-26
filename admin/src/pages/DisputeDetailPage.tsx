import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';

interface DisputeDetail {
  id: string;
  serviceRequestId: string;
  abertaPor: string;
  motivo: string;
  descricao?: string;
  valorRetido: number;
  status: string;
  criadaEm: string;
  resolvidaEm?: string;
  decisao?: string;
  justificativaAdmin?: string;
  parteCliente?: { nome: string; email: string };
  partePrestador?: { nome: string; email: string };
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

  async function resolve(decisao: string) {
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
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Disputa · Chamado #{d.serviceRequestId?.slice(-4) || d.id.slice(-4)}</span>
        <span style={S.badge}>EM DISPUTA</span>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', gap: 20 }}>
        <div style={{ flex: 1.5, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={S.card}>
            <span style={{ fontSize: 14, fontWeight: 800, color: 'var(--text)' }}>Histórico</span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ display: 'flex', gap: 12 }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: '#15596E', marginTop: 6, flexShrink: 0 }} /><div><div style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>Cliente abriu disputa</div><div style={{ fontSize: 12, color: '#8A989B' }}>"{d.motivo}"</div></div></div>
              {d.descricao && <div style={{ display: 'flex', gap: 12 }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: '#15596E', marginTop: 6, flexShrink: 0 }} /><div><div style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>Prestador respondeu</div><div style={{ fontSize: 12, color: '#8A989B' }}>"{d.descricao}"</div></div></div>}
              <div style={{ display: 'flex', gap: 12 }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: '#14A8A0', marginTop: 6, flexShrink: 0 }} /><div><div style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>Em análise da mediação</div><div style={{ fontSize: 12, color: '#8A989B' }}>agora</div></div></div>
            </div>
          </div>
          <div style={S.card}>
            <span style={{ fontSize: 14, fontWeight: 800, color: 'var(--text)' }}>Mídias anexadas</span>
            <div style={{ display: 'flex', gap: 10 }}>
              {[0,1,2].map(i => <div key={i} style={{ width: 84, height: 84, borderRadius: 12, background: '#E6DDC9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="#8A7E66" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 8h3l1.5-2h9L18 8h3v11H3z"/><circle cx="12" cy="13" r="3.5"/></svg></div>)}
            </div>
          </div>
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={S.escrow}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 12, fontWeight: 800, letterSpacing: '0.05em', color: '#fff' }}><span style={{ width: 8, height: 8, borderRadius: '50%', background: '#B7DCE3' }} />VALOR RETIDO</span>
            <span style={{ fontSize: 30, fontWeight: 800, color: '#fff', letterSpacing: '-0.02em' }}>{fmt(d.valorRetido)}</span>
            <span style={{ fontSize: 12.5, color: '#B7DCE3' }}>Decida para quem o valor será liberado.</span>
          </div>
          <div style={S.card}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#0E2A33' }}>Resolver disputa</span>
            <button style={{ ...S.btnG, background: '#1B8C84' }} onClick={() => resolve('LIBERAR_PRESTADOR')} disabled={submitting}>
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg>Liberar ao prestador
            </button>
            <button style={{ ...S.btnG, background: '#DA6A32' }} onClick={() => resolve('REEMBOLSAR_CLIENTE')} disabled={submitting}>
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
