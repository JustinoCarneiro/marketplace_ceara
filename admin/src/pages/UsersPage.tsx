import { useEffect, useState } from 'react';
import { api } from '../api/client';

interface User { id: string; nome: string; email: string; role: string; status: string; }

const AVATAR_COLORS = ['#15596E', '#DA6A32', '#C0392B', '#1B8C84', '#3C7A4E', '#244C86'];
function avatarBg(n: string) { return AVATAR_COLORS[n.charCodeAt(0) % AVATAR_COLORS.length]; }
function initials(n: string) { return n.split(' ').slice(0, 2).map(s => s[0]).join('').toUpperCase(); }

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  async function load() {
    setLoading(true);
    try { const d = await api.get<User[]>('/admin/users'); setUsers(Array.isArray(d) ? d : []); }
    catch { setUsers([]); }
    finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function toggleStatus(id: string, current: string) {
    const action = current === 'ATIVO' ? 'suspend' : 'reactivate';
    try { await api.post(`/admin/users/${id}/${action}`, {}); load(); } catch {}
  }

  const filtered = search ? users.filter(u => u.nome.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase())) : users;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Gestão de usuários</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: '#F3ECDC', border: '1px solid #E6DDC9', borderRadius: 100, padding: '0 16px', height: 42, width: 320 }}>
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#8A989B" strokeWidth="2" strokeLinecap="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.5" y2="16.5"/></svg>
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Buscar por nome ou e-mail…" style={{ border: 'none', background: 'transparent', outline: 'none', fontSize: 13.5, color: '#0E2A33', flex: 1 }} />
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px' }}>
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> : (
          <div style={{ background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr 0.9fr 0.9fr', background: '#F3ECDC', padding: '14px 20px', borderBottom: '1px solid #E6DDC9' }}>
              <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E' }}>Usuário</span>
              <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E' }}>Perfil</span>
              <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E' }}>Status</span>
              <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', color: '#15596E', justifySelf: 'end' }}>Ação</span>
            </div>
            {filtered.map(u => {
              const isSuspended = u.status === 'SUSPENSO';
              return (
                <div key={u.id} style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr 0.9fr 0.9fr', padding: '14px 20px', borderBottom: '1px solid #E6DDC9', alignItems: 'center', background: isSuspended ? '#FBE6E2' : 'transparent' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <div style={{ width: 38, height: 38, borderRadius: 11, background: avatarBg(u.nome), color: '#fff', fontWeight: 700, fontSize: 13, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{initials(u.nome)}</div>
                    <div><div style={{ fontSize: 14, fontWeight: 700, color: '#0E2A33' }}>{u.nome}</div><div style={{ fontSize: 12, color: '#8A989B' }}>{u.email}</div></div>
                  </div>
                  <span style={{ fontSize: 13, color: '#4C636A' }}>{u.role === 'ROLE_PROVIDER' ? 'Prestador' : 'Cliente'}</span>
                  <span style={{ fontSize: 12, fontWeight: 700, color: isSuspended ? '#C0392B' : '#15756E', background: isSuspended ? '#fff' : '#DDF0EC', border: isSuspended ? '1px solid #E6BFA6' : 'none', padding: '4px 10px', borderRadius: 100, justifySelf: 'start' }}>{isSuspended ? 'SUSPENSO' : 'ATIVO'}</span>
                  <button onClick={() => toggleStatus(u.id, u.status)} style={{ height: 38, padding: '0 16px', border: isSuspended ? 'none' : '1.5px solid #C0392B', borderRadius: 100, background: isSuspended ? '#14A8A0' : 'transparent', color: isSuspended ? '#fff' : '#C0392B', fontWeight: 700, fontSize: 13, cursor: 'pointer', justifySelf: 'end' }}>{isSuspended ? 'Reativar' : 'Suspender'}</button>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
