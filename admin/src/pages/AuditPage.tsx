import { useEffect, useState } from 'react';
import { api } from '../api/client';

interface AuditLog { id: string; adminNome: string; acao: string; entidade: string; criadoEm: string; }

export default function AuditPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try { const d = await api.get<AuditLog[]>('/admin/audit-logs'); setLogs(Array.isArray(d) ? d : []); }
      catch { setLogs([]); }
      finally { setLoading(false); }
    })();
  }, []);

  function fmtDate(s: string) {
    const d = new Date(s);
    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }) + ' · ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', gap: 12, padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Log de auditoria</span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 12, fontWeight: 700, color: '#15596E', background: '#E2EEF2', padding: '5px 11px', borderRadius: 100 }}>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 018 0v3"/></svg>
          Somente leitura
        </span>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px' }}>
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> : (
          <div style={{ background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.3fr 1.2fr 1fr', background: '#F3ECDC', padding: '13px 20px', borderBottom: '1px solid #E6DDC9' }}>
              {['Admin', 'Ação', 'Entidade', 'Quando'].map(h => <span key={h} style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E' }}>{h}</span>)}
            </div>
            {logs.map(l => (
              <div key={l.id} style={{ display: 'grid', gridTemplateColumns: '1fr 1.3fr 1.2fr 1fr', padding: '14px 20px', borderBottom: '1px solid #E6DDC9', alignItems: 'center' }}>
                <span style={{ fontSize: 13, color: '#0E2A33', fontWeight: 600 }}>{l.adminNome}</span>
                <span style={{ fontSize: 13, color: '#4C636A' }}>{l.acao}</span>
                <span style={{ fontSize: 13, color: '#4C636A', fontFamily: 'monospace' }}>{l.entidade}</span>
                <span style={{ fontSize: 12.5, color: '#8A989B' }}>{fmtDate(l.criadoEm)}</span>
              </div>
            ))}
            {logs.length === 0 && <div style={{ padding: 28, textAlign: 'center', color: '#8A989B' }}>Nenhum registro encontrado.</div>}
          </div>
        )}
      </div>
    </div>
  );
}
