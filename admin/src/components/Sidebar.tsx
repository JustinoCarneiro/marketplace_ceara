import { NavLink, useNavigate } from 'react-router-dom';
import { clearToken } from '../store/auth';

const NAV = [
  { icon: '📊', label: 'Dashboard',       to: '/' },
  { icon: '⚖️', label: 'Disputas',        to: '/disputes' },
  { icon: '🛡️', label: 'Prestadores',     to: '/providers' },
  { icon: '👥', label: 'Usuários',        to: '/users' },
  { icon: '💳', label: 'Financeiro',      to: '/finance' },
  { icon: '🏷️', label: 'Categorias',      to: '/categories' },
  { icon: '🔔', label: 'Notificações',    to: '/notifications' },
  { icon: '📋', label: 'Auditoria',       to: '/audit' },
  { icon: '📥', label: 'Relatórios',      to: '/reports' },
];

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
      <div style={{
        padding: '24px 20px 20px',
        borderBottom: '1px solid rgba(183,220,227,0.15)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 28 }}>🌊</span>
          <div>
            <div style={{
              fontSize: 16,
              fontWeight: 800,
              color: 'var(--sidebar-active)',
              lineHeight: 1.1,
            }}>Onda</div>
            <div style={{
              fontSize: 11,
              fontWeight: 600,
              color: 'var(--sidebar-text)',
              textTransform: 'uppercase',
              letterSpacing: '0.15em',
            }}>Admin</div>
          </div>
        </div>
      </div>

      {/* Nav */}
      <nav style={{ flex: 1, padding: '12px 10px', overflowY: 'auto' }}>
        {NAV.map(item => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            style={({ isActive }) => ({
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              padding: '10px 12px',
              borderRadius: 10,
              color: isActive ? 'var(--sidebar-active)' : 'var(--sidebar-text)',
              background: isActive ? 'rgba(252,248,238,0.12)' : 'transparent',
              fontWeight: isActive ? 700 : 500,
              fontSize: 14,
              marginBottom: 2,
              transition: 'background var(--dur-fast)',
              textDecoration: 'none',
            })}
          >
            <span style={{ fontSize: 16, width: 20, textAlign: 'center' }}>{item.icon}</span>
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Logout */}
      <div style={{ padding: '12px 10px', borderTop: '1px solid rgba(183,220,227,0.15)' }}>
        <button
          onClick={logout}
          style={{
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '10px 12px',
            borderRadius: 10,
            color: 'var(--sidebar-text)',
            fontSize: 14,
            fontWeight: 500,
            cursor: 'pointer',
          }}
        >
          <span style={{ fontSize: 16, width: 20, textAlign: 'center' }}>🚪</span>
          Sair
        </button>
      </div>
    </aside>
  );
}
