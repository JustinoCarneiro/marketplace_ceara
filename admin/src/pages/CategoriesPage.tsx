import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../api/client';

interface Category { id: string; nome: string; slug: string; cor?: string; ativa: boolean; totalPrestadores?: number; }

const CAT_COLORS: Record<string, string> = { eletrica: '#F2B015', hidraulica: '#15596E', limpeza: '#1B8C84', pintura: '#DA6A32', reforma: '#244C86', jardinagem: '#3C7A4E', geral: '#14A8A0' };

export default function CategoriesPage() {
  const [cats, setCats] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [nome, setNome] = useState('');
  const [saving, setSaving] = useState(false);

  async function load() {
    setLoading(true);
    try { const d = await api.get<Category[]>('/admin/categories'); setCats(Array.isArray(d) ? d : []); }
    catch { setCats([]); }
    finally { setLoading(false); }
  }

  useEffect(() => { load(); }, []);

  async function create(e: FormEvent) {
    e.preventDefault();
    if (!nome.trim()) return;
    setSaving(true);
    try { await api.post('/admin/categories', { nome, slug: nome.toLowerCase().replace(/\s+/g, '_') }); setNome(''); setShowForm(false); load(); }
    catch {} finally { setSaving(false); }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Catálogo de categorias</span>
        <button onClick={() => setShowForm(!showForm)} style={{ display: 'inline-flex', alignItems: 'center', gap: 8, height: 44, padding: '0 18px', border: 'none', borderRadius: 100, background: '#14A8A0', color: '#fff', fontWeight: 700, fontSize: 14, cursor: 'pointer', boxShadow: '0 14px 24px -14px rgba(20,168,160,.85)' }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>Nova categoria
        </button>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', background: '#F6EEDC', padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 14 }}>
        {showForm && (
          <form onSubmit={create} style={{ background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, padding: 18, display: 'flex', gap: 12, alignItems: 'center' }}>
            <input value={nome} onChange={e => setNome(e.target.value)} placeholder="Nome da categoria" style={{ flex: 1, height: 42, border: '1px solid #E6DDC9', borderRadius: 12, padding: '0 14px', fontSize: 14, color: '#0E2A33', background: '#fff', outline: 'none' }} />
            <button type="submit" disabled={saving} style={{ height: 42, padding: '0 18px', border: 'none', borderRadius: 100, background: '#14A8A0', color: '#fff', fontWeight: 700, fontSize: 14, cursor: 'pointer' }}>{saving ? 'Salvando…' : 'Criar'}</button>
          </form>
        )}
        {loading ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 200 }}><div className="spinner" /></div> : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 14, alignContent: 'start' }}>
            {cats.map(c => (
              <div key={c.id} style={{ background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, padding: '16px 18px', display: 'flex', alignItems: 'center', gap: 14, opacity: c.ativa ? 1 : 0.6 }}>
                <span style={{ width: 14, height: 14, borderRadius: 5, background: CAT_COLORS[c.slug] || '#14A8A0', flexShrink: 0 }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 15, fontWeight: 700, color: '#0E2A33' }}>{c.nome}</div>
                  <div style={{ fontSize: 12.5, color: '#8A989B' }}>{c.totalPrestadores ?? 0} prestadores</div>
                </div>
                <span style={{ fontSize: 12, fontWeight: 800, color: c.ativa ? '#15756E' : '#8A989B', background: c.ativa ? '#DDF0EC' : '#EAE0CB', padding: '4px 10px', borderRadius: 100 }}>{c.ativa ? 'ATIVA' : 'INATIVA'}</span>
                <span style={{ fontSize: 13, fontWeight: 700, color: '#14A8A0', cursor: 'pointer' }}>Editar</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
