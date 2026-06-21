import { useEffect, useState } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface Transaction {
  id: string;
  serviceRequestId: string;
  valorTotal: number;
  statusPagamento: string;
  criadaEm: string;
}

interface OutboxEvent {
  id: string;
  agregado: string;
  tipoEvento: string;
  tentativas: number;
  status: string;
  criadoEm: string;
}

export default function FinancePage() {
  const [tab, setTab] = useState<'transactions' | 'outbox'>('transactions');
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [outbox, setOutbox] = useState<OutboxEvent[]>([]);
  const [txStatus, setTxStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [reprocessing, setReprocessing] = useState<string | null>(null);
  const [msg, setMsg] = useState<{ id: string; type: 'ok' | 'err'; text: string } | null>(null);

  async function loadTransactions() {
    setLoading(true);
    try {
      const data = await api.get<Transaction[]>(`/admin/transactions?status=${txStatus}`);
      setTransactions(Array.isArray(data) ? data : []);
    } catch {
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  }

  async function loadOutbox() {
    setLoading(true);
    try {
      const data = await api.get<OutboxEvent[]>('/admin/outbox?status=FALHA');
      setOutbox(Array.isArray(data) ? data : []);
    } catch {
      setOutbox([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (tab === 'transactions') loadTransactions();
    else loadOutbox();
  }, [tab, txStatus]);

  async function reprocess(eventId: string) {
    setReprocessing(eventId);
    try {
      await api.post(`/admin/outbox/${eventId}/reprocess`);
      setMsg({ id: eventId, type: 'ok', text: 'Evento reenfileirado com sucesso.' });
      setTimeout(() => loadOutbox(), 1500);
    } catch (e: unknown) {
      setMsg({ id: eventId, type: 'err', text: e instanceof Error ? e.message : 'Erro.' });
    } finally {
      setReprocessing(null);
    }
  }

  function fmt(n: number) {
    return n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  function fmtDate(s: string) {
    return new Date(s).toLocaleString('pt-BR', {
      day: '2-digit', month: '2-digit', year: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 1200 }}>
      <PageHeader
        title="Reconciliação Financeira"
        subtitle="Transações de escrow e reprocessamento de eventos Outbox"
      />

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 24, borderBottom: '1px solid var(--line)' }}>
        {(['transactions', 'outbox'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            style={{
              padding: '10px 20px',
              fontWeight: tab === t ? 700 : 500,
              color: tab === t ? 'var(--primary)' : 'var(--text-soft)',
              borderBottom: `2px solid ${tab === t ? 'var(--primary)' : 'transparent'}`,
              fontSize: 'var(--fs-body-sm)',
              marginBottom: -1,
              transition: 'all var(--dur-fast)',
            }}
          >
            {t === 'transactions' ? '💳 Transações' : '🔄 Outbox (falhas)'}
          </button>
        ))}
      </div>

      {/* Transactions tab */}
      {tab === 'transactions' && (
        <>
          <div style={{ marginBottom: 20 }}>
            <select
              className="select-field"
              value={txStatus}
              onChange={e => setTxStatus(e.target.value)}
              style={{ width: 180 }}
            >
              <option value="">Todos os status</option>
              <option value="PENDENTE">Pendente</option>
              <option value="RETIDO">Retido</option>
              <option value="LIBERADO">Liberado</option>
              <option value="REEMBOLSADO">Reembolsado</option>
            </select>
          </div>

          {loading ? (
            <div className="loading-center"><div className="spinner" /></div>
          ) : transactions.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state__icon">💳</div>
              <div className="empty-state__title">Nenhuma transação</div>
              <div className="empty-state__body">Nenhuma transação com o status selecionado.</div>
            </div>
          ) : (
            <div className="card" style={{ overflowX: 'auto' }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>ID Transação</th>
                    <th>Pedido</th>
                    <th>Valor total</th>
                    <th>Status escrow</th>
                    <th>Data</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map(tx => (
                    <tr key={tx.id}>
                      <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{tx.id.slice(0, 10)}</td>
                      <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{tx.serviceRequestId.slice(0, 8)}</td>
                      <td style={{ fontWeight: 700, color: 'var(--institutional)' }}>{fmt(tx.valorTotal)}</td>
                      <td>
                        <span className={`badge badge--${tx.statusPagamento.toLowerCase()}`}>
                          {tx.statusPagamento}
                        </span>
                      </td>
                      <td style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>
                        {fmtDate(tx.criadaEm)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Outbox tab */}
      {tab === 'outbox' && (
        <>
          {outbox.length > 0 && (
            <div className="alert alert--warning" style={{ marginBottom: 16 }}>
              <span>⚠️</span>
              <span>
                <strong>{outbox.length} evento{outbox.length !== 1 ? 's' : ''} com falha</strong> —
                Reprocesse com idempotência para evitar duplicações.
              </span>
            </div>
          )}

          {loading ? (
            <div className="loading-center"><div className="spinner" /></div>
          ) : outbox.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state__icon">✅</div>
              <div className="empty-state__title">Nenhuma falha no Outbox</div>
              <div className="empty-state__body">Todos os eventos foram processados com sucesso.</div>
            </div>
          ) : (
            <div className="card" style={{ overflowX: 'auto' }}>
              <table className="table">
                <thead>
                  <tr>
                    <th>Evento</th>
                    <th>Agregado</th>
                    <th>Tipo</th>
                    <th>Tentativas</th>
                    <th>Status</th>
                    <th>Data</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {outbox.map(ev => (
                    <>
                      <tr key={ev.id}>
                        <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{ev.id.slice(0, 10)}</td>
                        <td style={{ fontSize: 'var(--fs-caption)' }}>{ev.agregado}</td>
                        <td style={{ fontWeight: 600 }}>{ev.tipoEvento}</td>
                        <td>
                          <span style={{
                            display: 'inline-block',
                            width: 28, height: 28,
                            borderRadius: '50%',
                            background: ev.tentativas >= 3 ? 'var(--danger-tint)' : 'var(--sun-tint)',
                            color: ev.tentativas >= 3 ? 'var(--danger-ink)' : 'var(--sun-ink)',
                            textAlign: 'center', lineHeight: '28px',
                            fontSize: 12, fontWeight: 700,
                          }}>
                            {ev.tentativas}
                          </span>
                        </td>
                        <td>
                          <span className={`badge badge--${ev.status.toLowerCase()}`}>{ev.status}</span>
                        </td>
                        <td style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>
                          {fmtDate(ev.criadoEm)}
                        </td>
                        <td>
                          <button
                            className="btn btn--outline btn--sm"
                            disabled={reprocessing === ev.id}
                            onClick={() => reprocess(ev.id)}
                          >
                            {reprocessing === ev.id ? 'Reenfileirando…' : 'Reprocessar'}
                          </button>
                        </td>
                      </tr>
                      {msg?.id === ev.id && (
                        <tr key={`${ev.id}-msg`}>
                          <td colSpan={7} style={{ padding: '0 16px 12px' }}>
                            <div className={`alert alert--${msg.type === 'ok' ? 'success' : 'danger'}`}>
                              <span>{msg.type === 'ok' ? '✅' : '⚠️'}</span>
                              <span>{msg.text}</span>
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
        </>
      )}
    </div>
  );
}
