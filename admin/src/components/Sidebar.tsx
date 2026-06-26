import { NavLink, useNavigate } from 'react-router-dom';
import { clearToken } from '../store/auth';

/* SVG icons extracted from prototype F29 sidebar */
const ICONS: Record<string, React.ReactNode> = {
  dashboard: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/>
      <rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>
    </svg>
  ),
  disputes: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.3 3.9 2 18a2 2 0 001.7 3h16.6a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z"/>
      <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
    </svg>
  ),
  moderation: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z"/>
      <path d="M9 12l2 2 4-4"/>
    </svg>
  ),
  users: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3 2.7-5 6-5s6 2 6 5"/>
      <path d="M16 4a3.2 3.2 0 010 8"/>
    </svg>
  ),
  finance: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="5" width="20" height="14" rx="3"/><line x1="2" y1="10" x2="22" y2="10"/>
    </svg>
  ),
  categories: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 7a2 2 0 012-2h6l9 9-7 7-9-9z"/>
      <circle cx="8" cy="10" r="1.4" fill="currentColor" stroke="none"/>
    </svg>
  ),
  audit: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 3h11l3 3v15H5z"/>
      <line x1="9" y1="9" x2="15" y2="9"/><line x1="9" y1="13" x2="15" y2="13"/>
    </svg>
  ),
  notifications: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9z"/>
      <path d="M9.5 21a2.5 2.5 0 005 0"/>
    </svg>
  ),
  reports: (
    <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3v12"/><polyline points="7 11 12 16 17 11"/><path d="M4 20h16"/>
    </svg>
  ),
  logout: (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 17l5-5-5-5"/><path d="M21 12H9"/><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/>
    </svg>
  ),
};

const NAV: { icon: keyof typeof ICONS; label: string; to: string; badge?: string }[] = [
  { icon: 'dashboard',     label: 'Dashboard',    to: '/' },
  { icon: 'disputes',      label: 'Disputas',     to: '/disputes',      badge: 'terra' },
  { icon: 'moderation',    label: 'Moderação',    to: '/providers' },
  { icon: 'users',         label: 'Usuários',     to: '/users' },
  { icon: 'finance',       label: 'Financeiro',   to: '/finance' },
  { icon: 'categories',    label: 'Categorias',   to: '/categories' },
  { icon: 'audit',         label: 'Auditoria',    to: '/audit' },
  { icon: 'notifications', label: 'Notificações', to: '/notifications', badge: 'primary' },
  { icon: 'reports',       label: 'Relatórios',   to: '/reports' },
];

/* Wave logo SVG — from prototype */
function WaveLogo() {
  return (
    <svg width="26" height="18" viewBox="0 0 46 32" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round">
      <path d="M3 20c5-12 11-12 16 0s11 12 16 0 8-8 8-8"/>
    </svg>
  );
}

export default function Sidebar() {
  const navigate = useNavigate();

  function logout() {
    clearToken();
    navigate('/login');
  }

  return (
    <aside style={{
      width: 'var(--sidebar-width)',
      background: 'var(--sidebar-bg)',
      display: 'flex',
      flexDirection: 'column',
      height: '100vh',
      position: 'fixed',
      top: 0,
      left: 0,
      zIndex: 100,
    }}>
      {/* Logo */}
      <div style={{ padding: '22px 22px 22px', borderBottom: '1px solid rgba(183,220,227,0.12)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 11 }}>
          <div style={{
            width: 40, height: 40, borderRadius: 13,
            background: 'var(--primary)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}>
            <WaveLogo />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
            <span style={{ fontSize: 18, fontWeight: 800, color: '#fff', letterSpacing: '-0.02em' }}>Onda</span>
            <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.16em', color: '#B7DCE3' }}>ADMIN</span>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav style={{ flex: 1, padding: '8px 12px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 3 }}>
        {NAV.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              position: 'relative',
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              padding: '11px 14px',
              borderRadius: 12,
              color: isActive ? '#fff' : '#B7DCE3',
              background: isActive ? '#15596E' : 'transparent',
              fontWeight: isActive ? 700 : 600,
              fontSize: 14,
              textDecoration: 'none',
              transition: 'background 180ms',
            })}
          >
            {({ isActive }) => (
              <>
                {isActive && (
                  <span style={{
                    position: 'absolute',
                    left: 0, top: 8, bottom: 8,
                    width: 3, borderRadius: 100,
                    background: 'var(--primary)',
                  }} />
                )}
                {ICONS[item.icon]}
                <span style={{ flex: 1 }}>{item.label}</span>
                {item.badge === 'terra' && (
                  <span style={{
                    background: 'var(--warm-terra)', color: '#fff',
                    fontSize: 12, fontWeight: 700,
                    padding: '1px 7px', borderRadius: 100,
                  }}>3</span>
                )}
                {item.badge === 'primary' && (
                  <span style={{
                    background: 'var(--primary)', color: '#fff',
                    fontSize: 12, fontWeight: 700,
                    padding: '1px 7px', borderRadius: 100,
                  }}>4</span>
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Logout */}
      <div style={{ padding: '12px 12px 20px', borderTop: '1px solid rgba(183,220,227,0.12)' }}>
        <button
          onClick={logout}
          style={{
            width: '100%',
            display: 'flex', alignItems: 'center', gap: 12,
            padding: '11px 14px',
            borderRadius: 12,
            color: '#B7DCE3',
            fontSize: 14, fontWeight: 600,
            cursor: 'pointer',
            transition: 'background 180ms',
          }}
          onMouseEnter={e => (e.currentTarget.style.background = 'rgba(183,220,227,0.08)')}
          onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
        >
          {ICONS.logout}
          Sair
        </button>
      </div>
    </aside>
  );
}
