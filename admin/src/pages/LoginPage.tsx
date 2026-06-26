import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { setToken } from '../store/auth';

const BASE = 'http://localhost:8080/api/v1';

function WaveLogo() {
  return (
    <svg width="30" height="21" viewBox="0 0 46 32" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round">
      <path d="M3 20c5-12 11-12 16 0s11 12 16 0 8-8 8-8"/>
    </svg>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`${BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, senha: password }),
      });
      if (!res.ok) {
        setError('Credenciais inválidas. Verifique e-mail e senha.');
        return;
      }
      const data = await res.json() as { accessToken: string; role: string };
      if (data.role !== 'ROLE_ADMIN') {
        setError('Acesso restrito a administradores.');
        return;
      }
      setToken(data.accessToken);
      navigate('/', { replace: true });
    } catch {
      setError('Erro de conexão com o servidor.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{
      flex: 1,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--institutional)',
      padding: '24px',
    }}>
      <div style={{
        width: '100%',
        maxWidth: 420,
        background: 'var(--surface)',
        borderRadius: 24,
        padding: 36,
        display: 'flex',
        flexDirection: 'column',
        gap: 22,
        boxShadow: '0 30px 60px -30px rgba(0,0,0,0.4)',
      }}>
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 48, height: 48, borderRadius: 15,
            background: 'var(--primary)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}>
            <WaveLogo />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
            <span style={{ fontSize: 22, fontWeight: 800, color: 'var(--text)', letterSpacing: '-0.02em' }}>Onda Admin</span>
            <span style={{ fontSize: 12, fontWeight: 700, letterSpacing: '0.14em', color: 'var(--institutional-2)' }}>PAINEL DE MEDIAÇÃO</span>
          </div>
        </div>

        {/* Restricted access badge */}
        <span style={{
          display: 'inline-flex', alignSelf: 'flex-start', alignItems: 'center', gap: 6,
          background: 'var(--institutional)', color: '#fff',
          fontSize: 12, fontWeight: 700, letterSpacing: '0.05em',
          padding: '6px 12px', borderRadius: 100,
        }}>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="5" y="11" width="14" height="9" rx="2"/>
            <path d="M8 11V8a4 4 0 018 0v3"/>
          </svg>
          ACESSO RESTRITO
        </span>

        {/* Form */}
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <label style={{
              fontSize: 12, fontWeight: 600, letterSpacing: '0.1em',
              textTransform: 'uppercase', color: 'var(--institutional-2)',
            }}>
              E-mail corporativo
            </label>
            <input
              className="input-field"
              type="email"
              placeholder="admin@onda.com.br"
              value={email}
              onChange={e => setEmail(e.target.value)}
              required
              autoComplete="email"
              style={{ background: '#fff', borderColor: 'var(--line-soft)', height: 50 }}
            />
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            <label style={{
              fontSize: 12, fontWeight: 600, letterSpacing: '0.1em',
              textTransform: 'uppercase', color: 'var(--institutional-2)',
            }}>
              Senha
            </label>
            <input
              className="input-field"
              type="password"
              placeholder="••••••••••"
              value={password}
              onChange={e => setPassword(e.target.value)}
              required
              autoComplete="current-password"
              style={{ background: '#fff', borderColor: 'var(--line-soft)', height: 50, letterSpacing: password ? '3px' : 'normal' }}
            />
          </div>

          {error && (
            <div className="alert alert--danger" style={{ borderRadius: 'var(--r-field)' }}>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M10.3 3.9 2 18a2 2 0 001.7 3h16.6a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              <span>{error}</span>
            </div>
          )}

          <button
            type="submit"
            className="btn btn--primary"
            disabled={loading}
            style={{ width: '100%', height: 54, fontSize: 'var(--fs-body)', marginTop: 4, boxShadow: '0 16px 26px -14px rgba(20,168,160,0.85)' }}
          >
            {loading ? 'Entrando…' : 'Entrar no painel'}
          </button>
        </form>

        {/* Audit notice */}
        <span style={{
          textAlign: 'center', fontSize: 12, color: 'var(--text-faint)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6,
        }}>
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 3h11l3 3v15H5z"/>
            <line x1="9" y1="12" x2="15" y2="12"/>
          </svg>
          Acesso monitorado e auditado.
        </span>
      </div>
    </div>
  );
}
