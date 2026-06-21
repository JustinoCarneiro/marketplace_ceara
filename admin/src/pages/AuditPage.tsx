import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface AuditEntry {
  id: string;
  adminId: string;
  adminNome?: string;
  acao: string;
  entidade: string;
  entidadeId: string;
  detalhe?: Record<string, unknown>;
  criadoEm: string;
}

const ACAO_ICON: Record<string, string> = {
  VERIFICAR_PRESTADOR: '🛡️',
  REPROVAR_PRESTADOR: '❌',
  SUSPENDER_USUARIO: '🚫',
  REATIVAR_USUARIO: '✅',
  RESOLVER_DISPUTA: '⚖️',
  CRIAR_CATEGORIA: '🏷️',
  ATUALIZAR_CATEGORIA: '✏️',
  REPROCESSAR_OUTBOX: '🔄',
};

export default function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [entidade, setEntidade] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);

  async function load(e?: FormEvent) {
    e?.preventDefault();
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (entidade) params.set('entidade', entidade);
      const data = await api.get<AuditEntry[]>(`/admin/audit?${params}`);
      setEntries(Array.isArray(data) ? data : []);
    } catch {
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  function fmtDate(s: string) {
    return new Date(s).toLocaleString('pt-BR', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 1100 }}>
      <PageHeader
        title="Log de Auditoria"
        subtitle="Registro imutável de todas as ações administrativas (quem, o quê, quando)"
      />

      <div className="alert alert--info" style={{ marginBottom: 20 }}>
        <span>🔒</span>
        <span style={{ fontSize: 'var(--fs-body-sm)' }}>
          Este log é <strong>append-only</strong> e não pode ser alterado ou deletado.
          Toda ação administrativa registra automaticamente o administrador responsável.
        </span>
      </div>

      {/* Filters */}
      <form onSubmit={load} style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
        <input
          className="input-field"
          placeholder="Filtrar por entidade (ex: USUARIO, DISPUTA, PRESTADOR)…"
          value={entidade}
          onChange={e => setEntidade(e.target.value.toUpperCase())}
          style={{ flex: 1, fontFamily: 'monospace', textTransform: 'uppercase' }}
        />
        <button type="submit" className="btn btn--primary" disabled={loading}>
          Filtrar
        </button>
        {entidade && (
          <button type="button" className="btn btn--ghost" onClick={() => { setEntidade(''); load(); }}>
            Limpar
          </button>
        )}
      </form>

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : entries.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__icon">📋</div>
          <div className="empty-state__title">Nenhum registro</div>
          <div className="empty-state__body">Nenhuma ação administrativa corresponde ao filtro.</div>
        </div>
      ) : (
        <div className="card" style={{ overflowX: 'auto' }}>
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 40 }}></th>
                <th>Ação</th>
                <th>Entidade</th>
                <th>Entidade ID</th>
                <th>Admin</th>
                <th>Data/Hora</th>
                <th style={{ width: 40 }}></th>
              </tr>
            </thead>
            <tbody>
              {entries.map(entry => (
                <>
                  <tr
                    key={entry.id}
                    style={{ cursor: entry.detalhe ? 'pointer' : 'default' }}
                    onClick={() => entry.detalhe && setExpanded(expanded === entry.id ? null : entry.id)}
                  >
                    <td style={{ textAlign: 'center', fontSize: 18 }}>
                      {ACAO_ICON[entry.acao] ?? '📌'}
                    </td>
                    <td>
                      <span style={{
                        fontFamily: 'monospace', fontSize: 13,
                        fontWeight: 600, color: 'var(--institutional)',
                      }}>{entry.acao}</span>
                    </td>
                    <td>
                      <span className="tag" style={{ fontFamily: 'monospace', fontSize: 12 }}>
                        {entry.entidade}
                      </span>
                    </td>
                    <td style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--text-faint)' }}>
                      #{entry.entidadeId.slice(0, 10)}
                    </td>
                    <td style={{ fontSize: 'var(--fs-body-sm)' }}>
                      {entry.adminNome ?? <span style={{ fontFamily: 'monospace', fontSize: 12 }}>#{entry.adminId.slice(0, 8)}</span>}
                    </td>
                    <td style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)', whiteSpace: 'nowrap' }}>
                      {fmtDate(entry.criadoEm)}
                    </td>
                    <td style={{ textAlign: 'center', color: 'var(--text-faint)', fontSize: 12 }}>
                      {entry.detalhe ? (expanded === entry.id ? '▲' : '▼') : ''}
                    </td>
                  </tr>
                  {expanded === entry.id && entry.detalhe && (
                    <tr key={`${entry.id}-detail`}>
                      <td colSpan={7} style={{ padding: '0 16px 16px' }}>
                        <div style={{
                          background: 'var(--bg-alt)',
                          borderRadius: 'var(--r-field)',
                          padding: 14,
                          fontFamily: 'monospace',
                          fontSize: 12,
                          color: 'var(--text-soft)',
                          whiteSpace: 'pre-wrap',
                          wordBreak: 'break-all',
                        }}>
                          {JSON.stringify(entry.detalhe, null, 2)}
                        </div>
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
