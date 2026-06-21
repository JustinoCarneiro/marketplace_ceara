import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface User {
  id: string;
  nome: string;
  email: string;
  role: string;
  status: string;
}

const ROLE_LABEL: Record<string, string> = {
  ROLE_CLIENT: 'Cliente',
  ROLE_PROVIDER: 'Prestador',
  ROLE_ADMIN: 'Admin',
};

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [q, setQ] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [acting, setActing] = useState<string | null>(null);
  const [msg, setMsg] = useState<{ id: string; type: 'ok' | 'err'; text: string } | null>(null);

  async function search(e?: FormEvent) {
    e?.preventDefault();
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (q) params.set('q', q);
      if (roleFilter) params.set('role', roleFilter);
      if (statusFilter) params.set('status', statusFilter);
      const data = await api.get<User[]>(`/admin/users?${params}`);
      setUsers(Array.isArray(data) ? data : []);
    } catch {
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { search(); }, [roleFilter, statusFilter]);

  async function suspend(userId: string) {
    setActing(userId);
    try {
      await api.post(`/admin/users/${userId}/suspend`);
      setMsg({ id: userId, type: 'ok', text: 'Usuário suspenso.' });
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, status: 'SUSPENSO' } : u));
    } catch (e: unknown) {
      setMsg({ id: userId, type: 'err', text: e instanceof Error ? e.message : 'Erro.' });
    } finally {
      setActing(null);
    }
  }

  async function reactivate(userId: string) {
    setActing(userId);
    try {
      await api.post(`/admin/users/${userId}/reactivate`);
      setMsg({ id: userId, type: 'ok', text: 'Usuário reativado.' });
      setUsers(prev => prev.map(u => u.id === userId ? { ...u, status: 'ATIVO' } : u));
    } catch (e: unknown) {
      setMsg({ id: userId, type: 'err', text: e instanceof Error ? e.message : 'Erro.' });
    } finally {
      setActing(null);
    }
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 1200 }}>
      <PageHeader
        title="Gestão de Usuários"
        subtitle="Busca, suporte e controle de acesso de clientes e prestadores"
      />

      {/* Filters */}
      <form onSubmit={search} style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        <input
          className="input-field"
          placeholder="Buscar por nome ou e-mail…"
          value={q}
          onChange={e => setQ(e.target.value)}
          style={{ flex: 1, minWidth: 200 }}
        />
        <select
          className="select-field"
          value={roleFilter}
          onChange={e => setRoleFilter(e.target.value)}
          style={{ width: 150 }}
        >
          <option value="">Todos os papéis</option>
          <option value="ROLE_CLIENT">Cliente</option>
          <option value="ROLE_PROVIDER">Prestador</option>
        </select>
        <select
          className="select-field"
          value={statusFilter}
          onChange={e => setStatusFilter(e.target.value)}
          style={{ width: 140 }}
        >
          <option value="">Todos os status</option>
          <option value="ATIVO">Ativo</option>
          <option value="SUSPENSO">Suspenso</option>
        </select>
        <button type="submit" className="btn btn--primary" disabled={loading}>
          {loading ? 'Buscando…' : 'Buscar'}
        </button>
      </form>

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : users.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__icon">👥</div>
          <div className="empty-state__title">Nenhum usuário encontrado</div>
          <div className="empty-state__body">Ajuste os filtros ou realize uma nova busca.</div>
        </div>
      ) : (
        <div className="card" style={{ overflowX: 'auto' }}>
          <table className="table">
            <thead>
              <tr>
                <th>Nome</th>
                <th>E-mail</th>
                <th>Papel</th>
                <th>Status</th>
                <th>ID</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map(u => (
                <>
                  <tr key={u.id}>
                    <td style={{ fontWeight: 600 }}>{u.nome}</td>
                    <td style={{ color: 'var(--text-soft)' }}>{u.email}</td>
                    <td><span className="tag">{ROLE_LABEL[u.role] ?? u.role}</span></td>
                    <td>
                      <span className={`badge badge--${u.status.toLowerCase()}`}>{u.status}</span>
                    </td>
                    <td style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--text-faint)' }}>
                      #{u.id.slice(0, 8)}
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 8 }}>
                        {u.status === 'ATIVO' ? (
                          <button
                            className="btn btn--danger btn--sm"
                            disabled={acting === u.id}
                            onClick={() => suspend(u.id)}
                          >
                            Suspender
                          </button>
                        ) : (
                          <button
                            className="btn btn--outline btn--sm"
                            disabled={acting === u.id}
                            onClick={() => reactivate(u.id)}
                          >
                            Reativar
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {msg?.id === u.id && (
                    <tr key={`${u.id}-msg`}>
                      <td colSpan={6} style={{ padding: '0 16px 12px' }}>
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
    </div>
  );
}
