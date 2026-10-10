import { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';

// Contrato real do backend (ProviderAdminDto): id (= userId), nome, categoria,
// statusVerificacao, notaMedia, cpfConciliado. Não existe endpoint de detalhe único — reaproveita
// a lista já usada por ProvidersPage e filtra pelo id.
// `cpfConciliado === false`: o mesmo CPF estava em outra conta quando o sistema passou a conferir; o prestador não opera
// nem aparece na busca até o suporte decidir (ação CONCILIAR_CPF). Só o `false` literal acende o aviso.
interface Provider {
  id: string;
  nome: string;
  categoria: string;
  statusVerificacao: string;
  notaMedia?: number;
  cpfConciliado?: boolean;
}

const AVATAR_COLORS = ['#15596E', '#3C7A4E', '#DA6A32', '#C0392B', '#1B8C84', '#244C86'];
function avatarBg(nome: string) { return AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length]; }
function initials(nome: string) { return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase(); }

const STATUS_MAP: Record<string, { bg: string; color: string; label: string }> = {
  EM_VERIFICACAO: { bg: '#F2B015', color: '#0E2A33', label: 'EM VERIFICAÇÃO' },
  VERIFICADO: { bg: '#0E3F52', color: '#fff', label: 'VERIFICADO' },
  REPROVADO: { bg: '#FBE6E2', color: '#C0392B', label: 'REPROVADO' },
  SUSPENSO: { bg: '#EAE0CB', color: '#4C636A', label: 'SUSPENSO' },
};

const S = {
  topbar: { height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', gap: 14, padding: '0 28px' } as React.CSSProperties,
  card: { background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column' as const, gap: 12 } as React.CSSProperties,
  btn: { height: 46, padding: '0 20px', border: 'none', borderRadius: 100, color: '#fff', fontWeight: 700, fontSize: 14, cursor: 'pointer' } as React.CSSProperties,
  textarea: { minHeight: 80, border: '1px solid #E6DDC9', borderRadius: 10, padding: 12, fontSize: 13.5, color: '#0E2A33', background: '#fff', outline: 'none', resize: 'vertical' as const, fontFamily: 'inherit' },
};

/**
 * Remonta a tela ao trocar de prestador (key={id}): `loading` volta a nascer true sem precisar de
 * setState dentro do efeito (react-hooks/set-state-in-effect) e a carga depende só do `id`.
 */
export default function ProviderDetailPage() {
  const { id } = useParams<{ id: string }>();
  return <ProviderDetail key={id} id={id} />;
}

function ProviderDetail({ id }: { id: string | undefined }) {
  const nav = useNavigate();
  const [provider, setProvider] = useState<Provider | null>(null);
  const [loading, setLoading] = useState(true);
  const [justificativa, setJustificativa] = useState('');
  const [justificativaCpf, setJustificativaCpf] = useState('');
  const [showSuspend, setShowSuspend] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState('');

  const carregar = useCallback(() =>
    api.get<Provider[]>('/admin/providers')
      .then(list => setProvider((Array.isArray(list) ? list : []).find(p => p.id === id) ?? null))
      .catch(() => setProvider(null))
      .finally(() => setLoading(false)),
  [id]);

  // Recarga depois de uma moderação: volta ao estado de carregamento, como sempre fez.
  function load() {
    setLoading(true);
    return carregar();
  }

  useEffect(() => { carregar(); }, [carregar]);

  async function moderate(action: 'APROVAR' | 'REPROVAR' | 'SUSPENDER' | 'CONCILIAR_CPF', texto: string = justificativa) {
    setErr(''); setSubmitting(true);
    try {
      await api.post(`/admin/providers/${id}/moderate`, { action, justificativa: texto.trim() || null });
      setJustificativa(''); setJustificativaCpf(''); setShowSuspend(false);
      await load();
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Erro ao registrar a ação.');
    } finally { setSubmitting(false); }
  }

  if (loading) return <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', flex: 1 }}><div className="spinner" /></div>;
  if (!provider) return <div style={{ padding: 36, color: 'var(--danger)' }}>Prestador não encontrado.</div>;

  const status = STATUS_MAP[provider.statusVerificacao] ?? STATUS_MAP.EM_VERIFICACAO;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={S.topbar}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#0E2A33" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" onClick={() => nav('/providers')} style={{ cursor: 'pointer' }}><polyline points="15 5 8 12 15 19"/></svg>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Perfil do prestador</span>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', gap: 20 }}>
        <div style={{ flex: 1.5, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ ...S.card, flexDirection: 'row', alignItems: 'center', gap: 16 }}>
            <div style={{ width: 56, height: 56, borderRadius: 14, background: avatarBg(provider.nome), color: '#fff', fontWeight: 800, fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{initials(provider.nome)}</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 18, fontWeight: 800, color: '#0E2A33' }}>{provider.nome}</div>
              <div style={{ fontSize: 13.5, color: '#4C636A' }}>{provider.categoria}</div>
            </div>
            <span style={{ fontSize: 12, fontWeight: 800, letterSpacing: '0.05em', background: status.bg, color: status.color, padding: '5px 11px', borderRadius: 100 }}>{status.label}</span>
          </div>
          <div style={S.card}>
            <span style={{ fontSize: 14, fontWeight: 800, color: 'var(--text)' }}>Reputação</span>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 13, color: '#606E71' }}>Nota média</span>
              <span style={{ fontSize: 13.5, color: '#0E2A33', fontWeight: 600 }}>{provider.notaMedia != null ? provider.notaMedia.toFixed(1) : '—'}</span>
            </div>
          </div>
        </div>

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {provider.cpfConciliado === false && (
            <div style={{ ...S.card, background: '#FDF3D6', border: '1.5px solid #F2B015' }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: '#0E2A33' }}>CPF em duplicidade</span>
              <span style={{ fontSize: 13, color: '#4C636A' }}>
                Este CPF já estava em outra conta quando o sistema passou a conferir. Até o suporte decidir, o prestador não envia
                propostas, não é contratado e não aparece na busca.
              </span>
              <textarea
                style={S.textarea}
                placeholder="O que foi verificado e por que esta é a conta verdadeira (obrigatório, fica no log de auditoria)"
                value={justificativaCpf}
                maxLength={500}
                onChange={e => setJustificativaCpf(e.target.value)}
              />
              <button
                style={{ ...S.btn, background: '#10847D', opacity: submitting || !justificativaCpf.trim() ? 0.5 : 1 }}
                onClick={() => moderate('CONCILIAR_CPF', justificativaCpf)}
                disabled={submitting || !justificativaCpf.trim()}
              >Conciliar CPF</button>
            </div>
          )}

          {provider.statusVerificacao === 'EM_VERIFICACAO' && (
            <div style={S.card}>
              <span style={{ fontSize: 13, fontWeight: 700, color: '#0E2A33' }}>Verificação pendente</span>
              <button style={{ ...S.btn, background: '#10847D' }} onClick={() => moderate('APROVAR')} disabled={submitting}>Verificar</button>
              <button style={{ ...S.btn, background: 'transparent', color: '#C0392B', border: '1.5px solid #C0392B' }} onClick={() => moderate('REPROVAR')} disabled={submitting}>Reprovar</button>
            </div>
          )}

          {provider.statusVerificacao === 'VERIFICADO' && (
            <div style={S.card}>
              <span style={{ fontSize: 13, fontWeight: 700, color: '#0E2A33' }}>Moderação</span>
              {!showSuspend ? (
                <button style={{ ...S.btn, background: '#C0392B' }} onClick={() => setShowSuspend(true)}>Suspender prestador</button>
              ) : (
                <>
                  <textarea
                    style={S.textarea}
                    placeholder="Justificativa da suspensão (opcional, fica no log de auditoria)"
                    value={justificativa}
                    maxLength={500}
                    onChange={e => setJustificativa(e.target.value)}
                  />
                  <button style={{ ...S.btn, background: '#C0392B' }} onClick={() => moderate('SUSPENDER')} disabled={submitting}>Confirmar suspensão</button>
                  <span onClick={() => { setShowSuspend(false); setJustificativa(''); }} style={{ fontSize: 13, fontWeight: 700, color: '#606E71', cursor: 'pointer', textAlign: 'center' }}>Cancelar</span>
                </>
              )}
            </div>
          )}

          {provider.statusVerificacao === 'SUSPENSO' && (
            <div style={S.card}>
              <span style={{ fontSize: 13, color: '#606E71' }}>Prestador suspenso. Não há reativação automática no painel — ajustar diretamente com a equipe de confiança e segurança.</span>
            </div>
          )}

          {err && <span style={{ fontSize: 13, color: '#C0392B' }}>{err}</span>}
        </div>
      </div>
    </div>
  );
}
