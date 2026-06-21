import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { setToken } from '../store/auth';

const BASE = 'http://localhost:8080/api/v1';

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
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--bg)',
      padding: '24px',
    }}>
      <div style={{ width: '100%', maxWidth: 400 }}>
        {/* Logo */}
        <div style={{ textAlign: 'center', marginBottom: 40 }}>
          <div style={{
            width: 64, height: 64,
            background: 'var(--institutional)',
            borderRadius: 18,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 16px',
            boxShadow: '0 12px 32px -8px rgba(14,63,82,0.5)',
          }}>
            <span style={{ fontSize: 32 }}>🌊</span>
          </div>
          <h1 style={{
            fontSize: 'var(--fs-h1)', fontWeight: 800,
            color: 'var(--text)', letterSpacing: '-0.02em',
          }}>Onda Admin</h1>
          <p style={{ marginTop: 6, fontSize: 'var(--fs-body-sm)', color: 'var(--text-soft)' }}>
            Painel de administração da plataforma
          </p>
        </div>

        {/* Card */}
        <div className="card" style={{ padding: 32 }}>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div>
              <label style={{
                display: 'block',
                fontSize: 'var(--fs-eyebrow)',
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.15em',
                color: 'var(--text-faint)',
                marginBottom: 8,
              }}>E-mail</label>
              <input
                className="input-field"
                type="email"
                placeholder="admin@onda.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>

            <div>
              <label style={{
                display: 'block',
                fontSize: 'var(--fs-eyebrow)',
                fontWeight: 600,
                textTransform: 'uppercase',
                letterSpacing: '0.15em',
                color: 'var(--text-faint)',
                marginBottom: 8,
              }}>Senha</label>
              <input
                className="input-field"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />
            </div>

            {error && (
              <div className="alert alert--danger" style={{ borderRadius: 'var(--r-field)' }}>
                <span>⚠️</span>
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              className="btn btn--primary"
              disabled={loading}
              style={{ width: '100%', height: 48, fontSize: 'var(--fs-body)', marginTop: 4 }}
            >
              {loading ? 'Entrando…' : 'Entrar no painel'}
            </button>
          </form>

          <div style={{
            marginTop: 24,
            display: 'flex', alignItems: 'center', gap: 8,
            padding: '10px 14px',
            background: 'var(--sky-tint)',
            borderRadius: 'var(--r-field)',
          }}>
            <span>🔒</span>
            <span style={{ fontSize: 'var(--fs-caption)', color: 'var(--institutional)' }}>
              Toda ação administrativa é registrada em log de auditoria imutável.
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
