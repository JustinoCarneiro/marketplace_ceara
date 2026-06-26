import { useEffect, useState } from 'react';
import { api } from '../api/client';

interface Notification { id: string; tipo: string; titulo: string; descricao: string; lida: boolean; criadaEm: string; link?: string; }

function iconFor(tipo: string) {
  if (tipo === 'SOS') return { bg: '#C0392B', el: <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.3 3.9 2 18a2 2 0 001.7 3h16.6a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg> };
  if (tipo === 'DISPUTA') return { bg: '#F7E3D6', el: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#C2572A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.3 3.9 2 18a2 2 0 001.7 3h16.6a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg> };
  if (tipo === 'VERIFICACAO') return { bg: '#FDF3D6', el: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#B5810A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z"/><line x1="12" y1="9" x2="12" y2="12.5"/><line x1="12" y1="15" x2="12.01" y2="15"/></svg> };
  return { bg: '#DDF0EC', el: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#15756E" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg> };
}

function timeAgo(s: string) {
  const ms = Date.now() - new Date(s).getTime();
  const m = Math.floor(ms / 60000);
  if (m < 60) return `há ${m} min`;
  const h = Math.floor(m / 60);
  if (h < 24) return `há ${h} h`;
  return `há ${Math.floor(h / 24)} d`;
}

export default function NotificationsPage() {
  const [notifs, setNotifs] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'unread'>('all');

  async function load() {
    setLoading(true);
    try { const d = await api.get<Notification[]>('/admin/notifications'); setNotifs(Array.isArray(d) ? d : []); }
    catch { setNotifs([]); }
    finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function markAllRead() {
    try { await api.post('/admin/notifications/mark-all-read', {}); load(); } catch {}
  }

  const unreadCount = notifs.filter(n => !n.lida).length;
  const filtered = filter === 'unread' ? notifs.filter(n => !n.lida) : notifs;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Central de notificações</span>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          <div style={{ position: 'relative', width: 44, height: 44, borderRadius: 12, background: '#F3ECDC', border: '1px solid #E6DDC9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#0E2A33" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9z"/><path d="M9.5 21a2.5 2.5 0 005 0"/></svg>
            {unreadCount > 0 && <span style={{ position: 'absolute', top: -5, right: -5, minWidth: 19, height: 19, padding: '0 5px', borderRadius: 100, background: '#14A8A0', color: '#fff', fontSize: 12, fontWeight: 800, display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid var(--surface)' }}>{unreadCount}</span>}
          </div>
        </div>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 18 }}>
        {/* Filter tabs */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span onClick={() => setFilter('all')} style={{ fontSize: 13, fontWeight: 700, color: filter === 'all' ? '#fff' : '#15596E', background: filter === 'all' ? '#0E3F52' : '#E2EEF2', padding: '8px 16px', borderRadius: 100, cursor: 'pointer' }}>Todas</span>
          <span onClick={() => setFilter('unread')} style={{ fontSize: 13, fontWeight: 700, color: '#15596E', background: '#E2EEF2', padding: '8px 16px', borderRadius: 100, cursor: 'pointer' }}>Não lidas · {unreadCount}</span>
          <span onClick={markAllRead} style={{ marginLeft: 'auto', fontSize: 13, fontWeight: 700, color: '#14A8A0', cursor: 'pointer' }}>Marcar todas como lidas</span>
        </div>

        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> :
         filtered.length === 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, gap: 16, padding: 40 }}>
            <div style={{ width: 84, height: 84, borderRadius: 26, background: '#E2EEF2', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9z"/><path d="M9.5 21a2.5 2.5 0 005 0"/><polyline points="9 11 11 13 15 9"/></svg>
            </div>
            <span style={{ fontSize: 18, fontWeight: 800, color: '#0E2A33' }}>Nenhum alerta pendente</span>
            <span style={{ fontSize: 14, color: '#4C636A', maxWidth: 320, lineHeight: 1.5, textAlign: 'center' }}>Quando houver um SOS, disputa ou verificação, ele aparece aqui.</span>
          </div>
        ) : filtered.map(n => {
          const icon = iconFor(n.tipo);
          const isSOS = n.tipo === 'SOS';
          return (
            <div key={n.id} style={{ background: isSOS ? '#FBE6E2' : 'var(--surface)', border: isSOS ? '1.5px solid #C0392B' : '1px solid var(--line-soft)', borderRadius: 12, padding: '18px 20px', display: 'flex', alignItems: 'center', gap: 16, opacity: n.lida && !isSOS ? 0.6 : 1, boxShadow: isSOS ? '0 14px 30px -22px rgba(192,57,43,.6)' : 'none' }}>
              {!n.lida && <span style={{ width: 9, height: 9, borderRadius: '50%', background: '#14A8A0', flexShrink: 0 }} />}
              {n.lida && <span style={{ width: 9, height: 9, flexShrink: 0 }} />}
              <div style={{ width: isSOS ? 48 : 44, height: isSOS ? 48 : 44, borderRadius: isSOS ? 12 : 13, background: icon.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{icon.el}</div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {isSOS && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: '#C0392B', color: '#fff', fontSize: 12, fontWeight: 800, letterSpacing: '0.06em', padding: '3px 9px', borderRadius: 100 }}>SOS</span>}
                  <span style={{ fontSize: isSOS ? 16 : 15.5, fontWeight: 800, color: '#0E2A33' }}>{n.titulo}</span>
                </div>
                <div style={{ fontSize: 13.5, color: isSOS ? '#9A2820' : '#4C636A', marginTop: 3 }}>{n.descricao}</div>
              </div>
              <span style={{ fontSize: 12.5, fontWeight: 600, color: isSOS ? '#9A2820' : '#8A989B', flexShrink: 0 }}>{timeAgo(n.criadaEm)}</span>
              {isSOS ? (
                <button style={{ height: 44, padding: '0 20px', border: 'none', borderRadius: 100, background: '#C0392B', color: '#fff', fontWeight: 700, fontSize: 14, cursor: 'pointer', flexShrink: 0, boxShadow: '0 12px 22px -12px rgba(192,57,43,.8)' }}>Ver pedido</button>
              ) : (
                <span style={{ fontSize: 13.5, fontWeight: 700, color: n.lida ? '#8A989B' : '#14A8A0', flexShrink: 0, cursor: 'pointer' }}>Ver →</span>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
