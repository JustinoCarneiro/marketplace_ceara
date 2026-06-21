import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

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

export default function DisputeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [dispute, setDispute] = useState<DisputeDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [decisao, setDecisao] = useState<'LIBERAR_PRESTADOR' | 'REEMBOLSAR_CLIENTE' | ''>('');
  const [justificativa, setJustificativa] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const data = await api.get<DisputeDetail>(`/admin/disputes/${id}`);
        setDispute(data);
      } catch {
        setDispute(null);
      } finally {
        setLoading(false);
      }
    })();
  }, [id]);

  async function resolve() {
    if (!decisao || !justificativa.trim()) {
      setErr('Selecione uma decisão e informe a justificativa.');
      return;
    }
    setErr('');
    setSubmitting(true);
    try {
      await api.post(`/admin/disputes/${id}/resolve`, { decisao, justificativa });
      setDone(true);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : 'Erro ao resolver disputa.');
    } finally {
      setSubmitting(false);
    }
  }

  function fmt(n: number) {
    return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  if (loading) return <div className="loading-center"><div className="spinner" /></div>;
  if (!dispute) return (
    <div style={{ padding: 36 }}>
      <div className="alert alert--danger"><span>⚠️</span><span>Disputa não encontrada.</span></div>
    </div>
  );

  return (
    <div style={{ padding: '32px 36px', maxWidth: 800 }}>
      <PageHeader
        title={`Disputa #${dispute.id.slice(0, 8)}`}
        subtitle={`Pedido #${dispute.serviceRequestId.slice(0, 8)}`}
        action={
          <button className="btn btn--ghost btn--sm" onClick={() => navigate('/disputes')}>
            ← Voltar
          </button>
        }
      />

      {done ? (
        <div className="alert alert--success" style={{ marginBottom: 24, borderRadius: 'var(--r-card)', padding: 24 }}>
          <span style={{ fontSize: 24 }}>✅</span>
          <div>
            <div style={{ fontWeight: 700 }}>Disputa resolvida com sucesso</div>
            <div style={{ fontSize: 'var(--fs-body-sm)', marginTop: 4 }}>
              Decisão registrada. O pagamento será processado conforme a resolução.
            </div>
            <button className="btn btn--ghost btn--sm" style={{ marginTop: 12 }} onClick={() => navigate('/disputes')}>
              Voltar para a fila
            </button>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {/* Parties */}
          <div className="card" style={{ padding: 24 }}>
            <p className="eyebrow" style={{ marginBottom: 16 }}>Partes envolvidas</p>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <div style={{
                background: 'var(--sky-tint)', borderRadius: 'var(--r-field)',
                padding: '14px 18px',
              }}>
                <div style={{ fontSize: 'var(--fs-eyebrow)', fontWeight: 600, color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.15em', marginBottom: 6 }}>
                  Cliente
                </div>
                <div style={{ fontWeight: 700 }}>{dispute.parteCliente?.nome ?? '—'}</div>
                <div style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-soft)' }}>{dispute.parteCliente?.email}</div>
              </div>
              <div style={{
                background: 'var(--sky-tint)', borderRadius: 'var(--r-field)',
                padding: '14px 18px',
              }}>
                <div style={{ fontSize: 'var(--fs-eyebrow)', fontWeight: 600, color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.15em', marginBottom: 6 }}>
                  Prestador
                </div>
                <div style={{ fontWeight: 700 }}>{dispute.partePrestador?.nome ?? '—'}</div>
                <div style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-soft)' }}>{dispute.partePrestador?.email}</div>
              </div>
            </div>
          </div>

          {/* Dispute info */}
          <div className="card" style={{ padding: 24 }}>
            <p className="eyebrow" style={{ marginBottom: 16 }}>Detalhes da disputa</p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-soft)' }}>Valor retido no escrow</span>
                <span style={{ fontWeight: 800, fontSize: 'var(--fs-h2)', color: 'var(--institutional)' }}>
                  {fmt(dispute.valorRetido)}
                </span>
              </div>
              <div className="divider" />
              <div>
                <div style={{ fontSize: 'var(--fs-eyebrow)', fontWeight: 600, color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.15em', marginBottom: 6 }}>
                  Motivo relatado
                </div>
                <div style={{ fontWeight: 600 }}>{dispute.motivo}</div>
              </div>
              {dispute.descricao && (
                <div>
                  <div style={{ fontSize: 'var(--fs-eyebrow)', fontWeight: 600, color: 'var(--text-faint)', textTransform: 'uppercase', letterSpacing: '0.15em', marginBottom: 6 }}>
                    Descrição
                  </div>
                  <div style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-soft)', lineHeight: 1.6 }}>
                    {dispute.descricao}
                  </div>
                </div>
              )}
              <div>
                <span className={`badge badge--${dispute.status.toLowerCase()}`}>{dispute.status}</span>
              </div>
            </div>
          </div>

          {/* Resolution */}
          {dispute.status === 'ABERTA' && (
            <div className="card" style={{ padding: 24, borderLeft: '4px solid var(--warm-terra)' }}>
              <p className="eyebrow" style={{ marginBottom: 16 }}>Resolução da disputa</p>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <div style={{ display: 'flex', gap: 12 }}>
                  <label style={{
                    flex: 1, display: 'flex', alignItems: 'flex-start', gap: 12,
                    padding: 16, border: `2px solid ${decisao === 'LIBERAR_PRESTADOR' ? 'var(--success)' : 'var(--line)'}`,
                    borderRadius: 'var(--r-field)', cursor: 'pointer',
                    background: decisao === 'LIBERAR_PRESTADOR' ? 'var(--success-tint)' : 'var(--surface)',
                    transition: 'all var(--dur-fast)',
                  }}>
                    <input
                      type="radio" name="decisao" value="LIBERAR_PRESTADOR"
                      checked={decisao === 'LIBERAR_PRESTADOR'}
                      onChange={() => setDecisao('LIBERAR_PRESTADOR')}
                    />
                    <div>
                      <div style={{ fontWeight: 700 }}>Liberar para o prestador</div>
                      <div style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-soft)', marginTop: 2 }}>
                        Serviço foi executado conforme combinado. {fmt(dispute.valorRetido)} transferido ao prestador.
                      </div>
                    </div>
                  </label>

                  <label style={{
                    flex: 1, display: 'flex', alignItems: 'flex-start', gap: 12,
                    padding: 16, border: `2px solid ${decisao === 'REEMBOLSAR_CLIENTE' ? 'var(--primary)' : 'var(--line)'}`,
                    borderRadius: 'var(--r-field)', cursor: 'pointer',
                    background: decisao === 'REEMBOLSAR_CLIENTE' ? '#E8FAF9' : 'var(--surface)',
                    transition: 'all var(--dur-fast)',
                  }}>
                    <input
                      type="radio" name="decisao" value="REEMBOLSAR_CLIENTE"
                      checked={decisao === 'REEMBOLSAR_CLIENTE'}
                      onChange={() => setDecisao('REEMBOLSAR_CLIENTE')}
                    />
                    <div>
                      <div style={{ fontWeight: 700 }}>Reembolsar o cliente</div>
                      <div style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-soft)', marginTop: 2 }}>
                        Serviço não foi entregue ou houve falha. {fmt(dispute.valorRetido)} devolvido ao cliente.
                      </div>
                    </div>
                  </label>
                </div>

                <div>
                  <label style={{
                    display: 'block',
                    fontSize: 'var(--fs-eyebrow)', fontWeight: 600,
                    textTransform: 'uppercase', letterSpacing: '0.15em',
                    color: 'var(--text-faint)', marginBottom: 8,
                  }}>
                    Justificativa (registrada em auditoria)
                  </label>
                  <textarea
                    className="textarea-field"
                    rows={4}
                    placeholder="Descreva os critérios que embasaram a decisão..."
                    value={justificativa}
                    onChange={e => setJustificativa(e.target.value)}
                  />
                </div>

                {err && (
                  <div className="alert alert--danger">
                    <span>⚠️</span><span>{err}</span>
                  </div>
                )}

                <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
                  <button className="btn btn--ghost" onClick={() => navigate('/disputes')}>
                    Cancelar
                  </button>
                  <button
                    className={`btn ${decisao === 'REEMBOLSAR_CLIENTE' ? 'btn--primary' : 'btn--primary'}`}
                    onClick={resolve}
                    disabled={submitting || !decisao}
                  >
                    {submitting ? 'Processando…' : 'Confirmar resolução'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {dispute.status !== 'ABERTA' && dispute.justificativaAdmin && (
            <div className="card" style={{ padding: 24, borderLeft: '4px solid var(--success)' }}>
              <p className="eyebrow" style={{ marginBottom: 12 }}>Resolução registrada</p>
              <div style={{ fontWeight: 700, marginBottom: 6 }}>{dispute.decisao}</div>
              <div style={{ fontSize: 'var(--fs-body-sm)', color: 'var(--text-soft)' }}>{dispute.justificativaAdmin}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
