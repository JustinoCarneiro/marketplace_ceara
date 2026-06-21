import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface Dispute {
  id: string;
  serviceRequestId: string;
  abertaPor: string;
  motivo: string;
  valorRetido: number;
  status: string;
  criadaEm: string;
}

export default function DisputesPage() {
  const navigate = useNavigate();
  const [disputes, setDisputes] = useState<Dispute[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('ABERTA');

  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Dispute[]>(`/admin/disputes?status=${statusFilter}`);
      setDisputes(Array.isArray(data) ? data : []);
    } catch {
      setDisputes([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [statusFilter]);

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
        title="Fila de Disputas"
        subtitle="Mediação de conflitos entre clientes e prestadores"
        action={
          <select
            className="select-field"
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}
            style={{ width: 160 }}
          >
            <option value="ABERTA">Abertas</option>
            <option value="RESOLVIDA">Resolvidas</option>
            <option value="">Todas</option>
          </select>
        }
      />

      {statusFilter === 'ABERTA' && disputes.length > 0 && (
        <div className="alert alert--warning" style={{ marginBottom: 20 }}>
          <span>⚠️</span>
          <span>
            <strong>{disputes.length} {disputes.length === 1 ? 'disputa aberta' : 'disputas abertas'}</strong> —
            O valor retido no escrow permanece bloqueado até a resolução.
          </span>
        </div>
      )}

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : disputes.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__icon">⚖️</div>
          <div className="empty-state__title">Nenhuma disputa {statusFilter === 'ABERTA' ? 'aberta' : 'encontrada'}</div>
          <div className="empty-state__body">
            {statusFilter === 'ABERTA'
              ? 'Ótimo! Não há disputas pendentes de mediação no momento.'
              : 'Nenhuma disputa corresponde ao filtro selecionado.'}
          </div>
        </div>
      ) : (
        <div className="card" style={{ overflowX: 'auto' }}>
          <table className="table">
            <thead>
              <tr>
                <th>Disputa</th>
                <th>Pedido</th>
                <th>Aberta por</th>
                <th>Motivo</th>
                <th>Valor retido</th>
                <th>Status</th>
                <th>Data</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {disputes.map(d => (
                <tr key={d.id}>
                  <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{d.id.slice(0, 8)}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: 12 }}>#{d.serviceRequestId.slice(0, 8)}</td>
                  <td style={{ fontSize: 'var(--fs-caption)' }}>{d.abertaPor}</td>
                  <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {d.motivo}
                  </td>
                  <td style={{ fontWeight: 700, color: 'var(--institutional)' }}>{fmt(d.valorRetido)}</td>
                  <td>
                    <span className={`badge badge--${d.status.toLowerCase()}`}>{d.status}</span>
                  </td>
                  <td style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>{fmtDate(d.criadaEm)}</td>
                  <td>
                    <button
                      className="btn btn--primary btn--sm"
                      onClick={() => navigate(`/disputes/${d.id}`)}
                    >
                      Mediar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
