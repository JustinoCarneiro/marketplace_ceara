import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../api/client';
import PageHeader from '../components/PageHeader';

interface Category {
  id: string;
  nome: string;
  slug: string;
  ativa: boolean;
}

const CAT_ICON: Record<string, string> = {
  eletrica: '⚡', hidraulica: '🔧', limpeza: '🧹',
  pintura: '🎨', reforma: '🏗️', jardinagem: '🌿', geral: '🔨',
};

export default function CategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [nome, setNome] = useState('');
  const [slug, setSlug] = useState('');
  const [saving, setSaving] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [editNome, setEditNome] = useState('');
  const [editAtiva, setEditAtiva] = useState(true);
  const [msg, setMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  async function load() {
    setLoading(true);
    try {
      const data = await api.get<Category[]>('/admin/categories');
      setCategories(Array.isArray(data) ? data : []);
    } catch {
      setCategories([]);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  function handleNomeChange(v: string) {
    setNome(v);
    setSlug(v.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, ''));
  }

  async function create(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      await api.post('/admin/categories', { nome, slug });
      setMsg({ type: 'ok', text: `Categoria "${nome}" criada.` });
      setNome(''); setSlug(''); setShowForm(false);
      load();
    } catch (err: unknown) {
      setMsg({ type: 'err', text: err instanceof Error ? err.message : 'Erro ao criar.' });
    } finally {
      setSaving(false);
    }
  }

  async function saveEdit(cat: Category) {
    setSaving(true);
    try {
      await api.patch(`/admin/categories/${cat.id}`, { nome: editNome, ativa: editAtiva });
      setMsg({ type: 'ok', text: `Categoria "${editNome}" atualizada.` });
      setEditId(null);
      load();
    } catch (err: unknown) {
      setMsg({ type: 'err', text: err instanceof Error ? err.message : 'Erro ao salvar.' });
    } finally {
      setSaving(false);
    }
  }

  function startEdit(cat: Category) {
    setEditId(cat.id);
    setEditNome(cat.nome);
    setEditAtiva(cat.ativa);
  }

  return (
    <div style={{ padding: '32px 36px', maxWidth: 900 }}>
      <PageHeader
        title="Catálogo de Categorias"
        subtitle="Serviços disponíveis na plataforma por bairro"
        action={
          <button className="btn btn--primary" onClick={() => setShowForm(v => !v)}>
            {showForm ? 'Cancelar' : '+ Nova categoria'}
          </button>
        }
      />

      {msg && (
        <div className={`alert alert--${msg.type === 'ok' ? 'success' : 'danger'}`} style={{ marginBottom: 20 }}>
          <span>{msg.type === 'ok' ? '✅' : '⚠️'}</span>
          <span>{msg.text}</span>
          <button onClick={() => setMsg(null)} style={{ marginLeft: 'auto', fontSize: 18, color: 'inherit' }}>×</button>
        </div>
      )}

      {/* New category form */}
      {showForm && (
        <div className="card" style={{ padding: 24, marginBottom: 24, borderLeft: '4px solid var(--primary)' }}>
          <p className="section-title" style={{ marginBottom: 16 }}>Nova categoria</p>
          <form onSubmit={create} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <div>
                <label style={{ display: 'block', fontSize: 'var(--fs-eyebrow)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.15em', color: 'var(--text-faint)', marginBottom: 8 }}>
                  Nome
                </label>
                <input
                  className="input-field"
                  placeholder="Ex: Elétrica"
                  value={nome}
                  onChange={e => handleNomeChange(e.target.value)}
                  required
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 'var(--fs-eyebrow)', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.15em', color: 'var(--text-faint)', marginBottom: 8 }}>
                  Slug (gerado automaticamente)
                </label>
                <input
                  className="input-field"
                  placeholder="eletrica"
                  value={slug}
                  onChange={e => setSlug(e.target.value)}
                  required
                  style={{ fontFamily: 'monospace' }}
                />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'flex-end' }}>
              <button type="button" className="btn btn--ghost" onClick={() => setShowForm(false)}>Cancelar</button>
              <button type="submit" className="btn btn--primary" disabled={saving}>
                {saving ? 'Criando…' : 'Criar categoria'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Categories list */}
      {loading ? (
        <div className="loading-center"><div className="spinner" /></div>
      ) : categories.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state__icon">🏷️</div>
          <div className="empty-state__title">Nenhuma categoria</div>
          <div className="empty-state__body">Crie a primeira categoria para habilitar serviços na plataforma.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {categories.map(cat => (
            <div key={cat.id} className="card" style={{
              padding: '16px 20px',
              display: 'flex', alignItems: 'center', gap: 16,
              opacity: cat.ativa ? 1 : 0.55,
            }}>
              {/* Icon */}
              <div style={{
                width: 44, height: 44, borderRadius: 12,
                background: cat.ativa ? 'var(--sky-tint)' : 'var(--bg-alt)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontSize: 22, flexShrink: 0,
              }}>
                {CAT_ICON[cat.slug] ?? '🏷️'}
              </div>

              {editId === cat.id ? (
                /* Edit mode */
                <div style={{ flex: 1, display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                  <input
                    className="input-field"
                    value={editNome}
                    onChange={e => setEditNome(e.target.value)}
                    style={{ width: 200 }}
                  />
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 'var(--fs-body-sm)', cursor: 'pointer' }}>
                    <input type="checkbox" checked={editAtiva} onChange={e => setEditAtiva(e.target.checked)} />
                    Ativa
                  </label>
                  <button className="btn btn--primary btn--sm" disabled={saving} onClick={() => saveEdit(cat)}>
                    Salvar
                  </button>
                  <button className="btn btn--ghost btn--sm" onClick={() => setEditId(null)}>Cancelar</button>
                </div>
              ) : (
                /* View mode */
                <>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 700, fontSize: 'var(--fs-h3)' }}>{cat.nome}</div>
                    <div style={{ fontSize: 'var(--fs-caption)', color: 'var(--text-faint)', fontFamily: 'monospace' }}>
                      /{cat.slug}
                    </div>
                  </div>
                  <span className={`badge badge--${cat.ativa ? 'ativo' : 'suspenso'}`}>
                    {cat.ativa ? 'Ativa' : 'Inativa'}
                  </span>
                  <button className="btn btn--ghost btn--sm" onClick={() => startEdit(cat)}>
                    Editar
                  </button>
                </>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
