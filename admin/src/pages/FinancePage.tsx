import { useEffect, useState } from 'react';
import { api, downloadFile } from '../api/client';

interface Transaction { id: string; serviceRequestId: string; prestadorNome?: string; valorTotal: number; statusPagamento: string; criadaEm: string; }
interface OutboxEvent { id: string; tipo: string; entidade: string; tentativas: number; status: string; }

// GET /admin/transactions filtra por 1 status só (?status=, default RETIDO) — sem essas 3
// chamadas em paralelo, os KPIs "Liberado"/"Reembolsado" e a tabela nunca mostravam nada
// além de RETIDO, mesmo com transações de verdade nos outros status.
const RECONCILE_STATUSES = ['RETIDO', 'LIBERADO', 'REEMBOLSADO'] as const;

function statusBadge(s: string) {
  const m: Record<string, { bg: string; color: string }> = {
    RETIDO: { bg: '#E2EEF2', color: '#15596E' },
    LIBERADO: { bg: '#DDF0EC', color: '#15756E' },
    REEMBOLSADO: { bg: '#F7E3D6', color: '#A94C25' },
    FALHA: { bg: '#FBE6E2', color: '#C0392B' },
  };
  const st = m[s] || m.RETIDO;
  return <span style={{ fontSize: 12, fontWeight: 800, color: st.color, background: st.bg, padding: '4px 10px', borderRadius: 100, justifySelf: 'start' }}>{s}</span>;
}

export default function FinancePage() {
  const [txs, setTxs] = useState<Transaction[]>([]);
  const [outbox, setOutbox] = useState<OutboxEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [exportErr, setExportErr] = useState('');
  const [reprocessingId, setReprocessingId] = useState<string | null>(null);
  const [reprocessErr, setReprocessErr] = useState('');

  async function load() {
    setLoading(true);
    setLoadError(false);
    try {
      let anyFailed = false;
      const [statuses, o] = await Promise.all([
        Promise.all(RECONCILE_STATUSES.map(s =>
          api.get<Transaction[]>(`/admin/transactions?status=${s}`).catch(() => { anyFailed = true; return []; }))),
        api.get<OutboxEvent[]>('/admin/outbox?status=FALHA').catch(() => { anyFailed = true; return []; }),
      ]);
      setTxs(statuses.flat());
      setOutbox(Array.isArray(o) ? o : []);
      setLoadError(anyFailed);
    } finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function reprocess(id: string) {
    setReprocessErr('');
    setReprocessingId(id);
    try {
      await api.post(`/admin/outbox/${id}/reprocess`, {});
      await load();
    } catch (e: unknown) {
      setReprocessErr(e instanceof Error ? e.message : 'Erro ao reprocessar.');
    } finally {
      setReprocessingId(null);
    }
  }

  async function exportar() {
    setExportErr('');
    try { await downloadFile('/admin/reports/transactions.csv', 'transacoes.csv'); }
    catch (e: unknown) { setExportErr(e instanceof Error ? e.message : 'Erro ao exportar'); }
  }

  const fmt = (n: number) => 'R$ ' + n.toLocaleString('pt-BR', { minimumFractionDigits: 0 });
  const retido = txs.filter(t => t.statusPagamento === 'RETIDO').reduce((s, t) => s + t.valorTotal, 0);
  const liberado = txs.filter(t => t.statusPagamento === 'LIBERADO').reduce((s, t) => s + t.valorTotal, 0);
  const reembolsado = txs.filter(t => t.statusPagamento === 'REEMBOLSADO').reduce((s, t) => s + t.valorTotal, 0);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Reconciliação financeira</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          {exportErr && <span style={{ fontSize: 12.5, color: '#C0392B' }}>{exportErr}</span>}
          <button onClick={exportar} style={{ display: 'inline-flex', alignItems: 'center', gap: 8, height: 44, padding: '0 18px', border: '1.5px solid #10847D', borderRadius: 100, background: 'var(--surface)', color: '#0E7D77', fontWeight: 700, fontSize: 14, cursor: 'pointer' }}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#10847D" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3v12"/><polyline points="7 11 12 16 17 11"/><path d="M4 20h16"/></svg>Exportar
          </button>
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> : (
          <>
            {loadError && (
              <div style={{ background: '#FDF3D6', border: '1px solid #F2B015', borderRadius: 12, padding: '12px 16px', fontSize: 13, color: '#8E6508' }}>
                Alguns dados financeiros não puderam ser carregados. Os números abaixo podem estar incompletos.
              </div>
            )}
            {reprocessErr && (
              <div style={{ background: '#FBE6E2', border: '1px solid #C0392B', borderRadius: 12, padding: '12px 16px', fontSize: 13, color: '#C0392B' }}>
                {reprocessErr}
              </div>
            )}
            {/* KPI cards */}
            <div style={{ display: 'flex', gap: 12 }}>
              {[
                { label: 'Retido', value: fmt(retido), color: '#15596E' },
                { label: 'Liberado (30d)', value: fmt(liberado), color: '#1B8C84' },
                { label: 'Reembolsado', value: fmt(reembolsado), color: '#DA6A32' },
              ].map(k => (
                <div key={k.label} style={{ flex: 1, background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, padding: '14px 16px' }}>
                  <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E' }}>{k.label}</div>
                  <div style={{ fontSize: 22, fontWeight: 800, color: k.color, marginTop: 4 }}>{k.value}</div>
                </div>
              ))}
            </div>

            {/* Outbox failures */}
            {outbox.length > 0 && (
              <div style={{ background: '#FBE6E2', border: '1.5px solid #C0392B', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#C0392B" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.3 3.9 2 18a2 2 0 001.7 3h16.6a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                  <span style={{ fontSize: 15, fontWeight: 800, color: '#0E2A33' }}>Fila outbox — {outbox.length} em FALHA</span>
                </div>
                {outbox.map(e => (
                  <div key={e.id} style={{ background: '#fff', borderRadius: 12, padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 14 }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13.5, fontWeight: 700, color: '#0E2A33', fontFamily: 'monospace' }}>{e.tipo}</div>
                      <div style={{ fontSize: 12, color: '#606E71' }}>{e.entidade} · {e.tentativas} tentativa{e.tentativas !== 1 ? 's' : ''}</div>
                    </div>
                    <span style={{ fontSize: 12, fontWeight: 800, color: '#C0392B', background: '#FBE6E2', padding: '4px 10px', borderRadius: 100 }}>FALHA</span>
                    <button onClick={() => reprocess(e.id)} disabled={reprocessingId === e.id} style={{ height: 40, padding: '0 16px', border: 'none', borderRadius: 100, background: '#10847D', color: '#fff', fontWeight: 700, fontSize: 13, cursor: reprocessingId === e.id ? 'default' : 'pointer', opacity: reprocessingId === e.id ? 0.6 : 1 }}>{reprocessingId === e.id ? 'Reprocessando…' : 'Reprocessar'}</button>
                  </div>
                ))}
                <span style={{ fontSize: 12, color: '#9A4A22' }}>Reprocessamento é idempotente — seguro repetir.</span>
              </div>
            )}

            {/* Transaction table */}
            <div style={{ background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, overflow: 'hidden' }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr 1fr 0.9fr', background: '#F3ECDC', padding: '13px 20px', borderBottom: '1px solid #E6DDC9' }}>
                {['Transação', 'Pedido', 'Valor', 'Status'].map(h => <span key={h} style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E' }}>{h}</span>)}
              </div>
              {txs.slice(0, 20).map(t => (
                <div key={t.id} style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr 1fr 0.9fr', padding: '13px 20px', borderBottom: '1px solid #E6DDC9', alignItems: 'center' }}>
                  <span style={{ fontSize: 13, color: '#4C636A', fontFamily: 'monospace' }}>tx_{t.id.slice(-4)}</span>
                  <span style={{ fontSize: 13, color: '#0E2A33' }}>#{t.serviceRequestId?.slice(-4)}{t.prestadorNome ? ` · ${t.prestadorNome}` : ''}</span>
                  <span style={{ fontSize: 13.5, fontWeight: 700, color: '#0E2A33' }}>{fmt(t.valorTotal)}</span>
                  {statusBadge(t.statusPagamento)}
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
