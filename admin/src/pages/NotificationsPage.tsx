import { useEffect, useState } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface Notification {
  id: string;
  tipo: 'SOS' | 'DISPUTA' | 'VERIFICACAO';
  refId: string;
  criadoEm: string;
  lida: boolean;
  descricao?: string;
}

const TIPO_CONFIG = {
  SOS: { icon: '🆘', label: 'SOS Acionado', bg: 'var(--danger-tint)', color: 'var(--danger-ink)', urgent: true },
  DISPUTA: { icon: '⚖️', label: 'Nova Disputa', bg: 'var(--terra-tint)', color: 'var(--terra-ink)', urgent: false },
  VERIFICACAO: { icon: '🛡️', label: 'Verificação inconclusiva', bg: 'var(--sun-tint)', color: 'var(--sun-ink)', urgent: false },
};

export default function NotificationsPage() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [showUnread, setShowUnread] = useState(true);

  async function load() {
    setLoading(true);
    try {
      const param = showUnread ? 'lida=false' : '';
      const data = await api.get<Notification[]>(`/admin/notifications?${param}`);
      setNotifications(Array.isArray(data) ? data : []);
    } catch {
      setNotifications([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [showUnread]);

  async function markRead(id: string) {
    try {
      await api.post(`/admin/notifications/${id}/read`);
      setNotifications(prev =>
        prev.map(n => n.id === id ? { ...n, lida: true } : n)
      );
    } catch {
      /* silent */
    }
  }

  async function markAllRead() {
    const unread = notifications.filter(n => !n.lida);
    await Promise.all(unread.map(n => api.post(`/admin/notifications/${n.id}/read`)));
    setNotifications(prev => prev.map(n => ({ ...n, lida: true })));
  }

  function fmtDate(s: string) {
    return new Date(s).toLocaleString('pt-BR', {
      day: '2-digit', month: '2-digit', year: '2-digit',
      hour: '2-digit', minute: '2-digit',
    });
  }

  const unreadCount = notifications.filter(n => !n.lida).length;
  const sos = notifications.filter(n => n.tipo === 'SOS' && !n.lida);

  return (
    <div style={{ padding: '32px 36px', maxWidth: 800 }}>
      <PageHeader
        title="Central de Notificações"
        subtitle="Alertas operacionais: SOS, disputas e verificações inconclusivas"
        action={
          <div style={{ display: 'flex', gap: 10 }}>
            {unreadCount > 0 && (
              <button className="btn btn--ghost btn--sm" onClick={markAllRead}>
                Marcar todas como lidas
              </button>
            )}
            <button
              className={`btn btn--sm ${showUnread ? 'btn--primary' : 'btn--ghost'}`}
              onClick={() => setShowUnread(v => !v)}
            >
              {showUnread ? 'Exibindo: não lidas' : 'Exibindo: todas'}
            </button>
          </div>
        }
      />

      {/* SOS banner */}
      {sos.length > 0 && (
        <div style={{
          background: 'var(--danger)',
          borderRadius: 'var(--r-card)',
          padding: '16px 20px',
          display: 'flex', alignItems: 'center', gap: 14,
          marginBottom: 20,
          color: '#fff',
          animation: 'pulse 2s infinite',
        }}>
          <span style={{ fontSize: 32 }}>🆘</span>
          <div>
            <div style={{ fontWeight: 800, fontSize: 'var(--fs-h3)' }}>
              {sos.length} SOS ativo{sos.length !== 1 ? 's' : ''}
            </div>
            <div style={{ fontSize: 'var(--fs-body-sm)', opacity: 0.9, marginTop: 2 }}>
              Emergência em andamento — verifique imediatamente e contate as partes.
            </div>
          </div>
        </div>
      )}

      <style>{`
        @keyframes pulse {
          0%, 100% { box-shadow: 0 0 0 0 rgba(192,57,43,0.5); }
          50% { box-shadow: 0 0 0 12px rgba(192,57,43,0); }
        }
      `}</style>

      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : notifications.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__icon">🔔</div>
          <div className="empty-state__title">Nenhuma notificação</div>
          <div className="empty-state__body">
            {showUnread
              ? 'Não há alertas não lidos. Você está em dia!'
              : 'Nenhuma notificação registrada no sistema.'}
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {notifications.map(n => {
            const cfg = TIPO_CONFIG[n.tipo];
            return (
              <div
                key={n.id}
                style={{
                  display: 'flex', alignItems: 'flex-start', gap: 14,
                  padding: '16px 20px',
                  background: n.lida ? 'var(--surface)' : cfg.bg,
                  border: `1px solid ${n.lida ? 'var(--line-soft)' : 'transparent'}`,
                  borderRadius: 'var(--r-card)',
                  transition: 'background var(--dur-fast)',
                  position: 'relative',
                  boxShadow: !n.lida ? 'var(--shadow-soft)' : 'none',
                }}
              >
                {/* Unread dot */}
                {!n.lida && (
                  <div style={{
                    position: 'absolute', top: 16, right: 16,
                    width: 8, height: 8, borderRadius: '50%',
                    background: cfg.urgent ? 'var(--danger)' : 'var(--primary)',
                  }} />
                )}

                <span style={{ fontSize: 28, flexShrink: 0 }}>{cfg.icon}</span>

                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <span style={{ fontWeight: 700, color: n.lida ? 'var(--text)' : cfg.color }}>
                      {cfg.label}
                    </span>
                    {cfg.urgent && !n.lida && (
                      <span className="badge badge--sos">Urgente</span>
                    )}
                  </div>

                  {n.descricao && (
                    <div style={{
                      fontSize: 'var(--fs-body-sm)',
                      color: 'var(--text-soft)',
                      marginTop: 4,
                      lineHeight: 1.5,
                    }}>{n.descricao}</div>
                  )}

                  <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)' }}>
                      {fmtDate(n.criadoEm)}
                    </span>
                    <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)', fontFamily: 'monospace' }}>
                      ref: #{n.refId.slice(0, 8)}
                    </span>
                  </div>
                </div>

                {!n.lida && (
                  <button
                    className="btn btn--ghost btn--sm"
                    style={{ flexShrink: 0 }}
                    onClick={() => markRead(n.id)}
                  >
                    Marcar como lida
                  </button>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
