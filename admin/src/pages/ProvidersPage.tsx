import { useEffect, useState } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface Provider {
  id: string;
  nome: string;
  email: string;
  categoria: string;
  statusVerificacao: 'EM_VERIFICACAO' | 'VERIFICADO' | 'REPROVADO';
  notaMedia: number | null;
}

const STATUS_LABEL = {
  EM_VERIFICACAO: 'Em verificação',
  VERIFICADO: 'Verificado',
  REPROVADO: 'Reprovado',
};

export default function ProvidersPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('EM_VERIFICACAO');
  const [justificativas, setJustificativas] = useState<Record<string, string>>({});
  const [acting, setActing] = useState<string | null>(null);
  const [msg, setMsg] = useState<{ id: string; type: 'ok' | 'err'; text: string } | null>(null);

  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Provider[]>(`/admin/providers?statusVerificacao=${statusFilter}`);
      setProviders(Array.isArray(data) ? data : []);
    } catch {
      setProviders([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [statusFilter]);

  async function decide(providerId: string, decisao: 'VERIFICADO' | 'REPROVADO') {
    const justificativa = justificativas[providerId]?.trim();
    if (!justificativa) {
      setMsg({ id: providerId, type: 'err', text: 'Informe a justificativa antes de decidir.' });
      return;
    }
    setActing(providerId);
    try {
      await api.post(`/admin/providers/${providerId}/verify`, { decisao, justificativa });
      setMsg({ id: providerId, type: 'ok', text: `Prestador ${decisao === 'VERIFICADO' ? 'verificado' : 'reprovado'} com sucesso.` });
      setTimeout(() => load(), 1500);
    } catch (e: unknown) {
      setMsg({ id: providerId, type: 'err', text: e instanceof Error ? e.message : 'Erro.' });
    } finally {
      setActing(null);
    }
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 1200 }}>
      <PageHeader
        title="Moderação de Prestadores"
        subtitle="Verificação de identidade e habilitação para operar na plataforma"
        action={
          <select
            className="select-field"
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}
            style={{ width: 180 }}
          >
            <option value="EM_VERIFICACAO">Em verificação</option>
            <option value="VERIFICADO">Verificados</option>
            <option value="REPROVADO">Reprovados</option>
            <option value="">Todos</option>
          </select>
        }
      />

      {statusFilter === 'EM_VERIFICACAO' && providers.length > 0 && (
        <div className="alert alert--info" style={{ marginBottom: 20 }}>
          <span>🛡️</span>
          <span>
            <strong>{providers.length} prestador{providers.length !== 1 ? 'es' : ''}</strong> aguardam verificação de identidade.
          </span>
        </div>
      )}

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : providers.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__icon">🛡️</div>
          <div className="empty-state__title">Nenhum prestador</div>
          <div className="empty-state__body">Nenhum prestador com o status selecionado.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {providers.map(p => (
            <div key={p.id} className="card" style={{ padding: 24 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
                {/* Avatar */}
                <div style={{
                  width: 52, height: 52, borderRadius: 14,
                  background: 'var(--bg-alt)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 24, flexShrink: 0,
                }}>👷</div>

                {/* Info */}
                <div style={{ flex: 1, minWidth: 200 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                    <span style={{ fontWeight: 700, fontSize: 'var(--fs-h3)' }}>{p.nome}</span>
                    <span className={`badge badge--${p.statusVerificacao.toLowerCase().replace('_', '')}`}>
                      {STATUS_LABEL[p.statusVerificacao]}
                    </span>
                  </div>
                  <div style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-soft)', marginTop: 4 }}>
                    {p.email}
                  </div>
                  <div style={{ display: 'flex', gap: 12, marginTop: 8, flexWrap: 'wrap' }}>
                    <span className="tag">{p.categoria}</span>
                    {p.notaMedia != null && (
                      <span className="tag">⭐ {p.notaMedia.toFixed(1)}</span>
                    )}
                    <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)', fontFamily: 'monospace' }}>
                      #{p.id.slice(0, 8)}
                    </span>
                  </div>
                </div>

                {/* Actions */}
                {p.statusVerificacao === 'EM_VERIFICACAO' && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 280 }}>
                    <textarea
                      className="textarea-field"
                      rows={2}
                      placeholder="Justificativa (obrigatória para aprovar ou reprovar)…"
                      value={justificativas[p.id] ?? ''}
                      onChange={e => setJustificativas(prev => ({ ...prev, [p.id]: e.target.value }))}
                    />

                    {msg?.id === p.id && (
                      <div className={`alert alert--${msg.type === 'ok' ? 'success' : 'danger'}`} style={{ borderRadius: 'var(--r-field)' }}>
                        <span>{msg.type === 'ok' ? '✅' : '⚠️'}</span>
                        <span>{msg.text}</span>
                      </div>
                    )}

                    <div style={{ display: 'flex', gap: 8 }}>
                      <button
                        className="btn btn--primary btn--sm"
                        style={{ flex: 1 }}
                        disabled={acting === p.id}
                        onClick={() => decide(p.id, 'VERIFICADO')}
                      >
                        ✓ Verificar
                      </button>
                      <button
                        className="btn btn--danger btn--sm"
                        style={{ flex: 1 }}
                        disabled={acting === p.id}
                        onClick={() => decide(p.id, 'REPROVADO')}
                      >
                        ✗ Reprovar
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
