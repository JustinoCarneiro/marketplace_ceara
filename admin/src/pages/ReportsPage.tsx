import { useState } from 'react';

export default function ReportsPage() {
  const [format, setFormat] = useState<'csv' | 'pdf'>('csv');
  const [generating, setGenerating] = useState(false);
  const [progress, setProgress] = useState(0);
  const [done, setDone] = useState(false);

  function generate() {
    setGenerating(true); setProgress(0); setDone(false);
    const iv = setInterval(() => {
      setProgress(p => {
        if (p >= 100) { clearInterval(iv); setGenerating(false); setDone(true); return 100; }
        return p + Math.random() * 18;
      });
    }, 300);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1 }}>
      <div style={{ height: 64, flexShrink: 0, background: 'var(--surface)', borderBottom: '1px solid var(--line-soft)', display: 'flex', alignItems: 'center', padding: '0 28px' }}>
        <span style={{ fontSize: 18, fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text)' }}>Exportar relatório</span>
      </div>
      <div style={{ flex: 1, background: '#F6EEDC', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 28 }}>
        <div style={{ width: 380, background: 'var(--surface)', border: '1px solid var(--line-soft)', borderRadius: 12, boxShadow: '0 28px 56px -20px rgba(14,42,51,.5)', padding: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
          {generating ? (
            <>
              <span style={{ fontSize: 16, fontWeight: 800, color: '#0E2A33', letterSpacing: '-0.01em' }}>Exportar relatório</span>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, padding: '14px 0' }}>
                <span style={{ width: 44, height: 44, borderRadius: '50%', border: '3.5px solid #B7DCE3', borderTopColor: '#14A8A0', display: 'inline-block', animation: 'spin 1s linear infinite' }} />
                <div style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 15, fontWeight: 700, color: '#0E2A33' }}>Gerando relatório…</span>
                  <span style={{ fontSize: 13, color: '#4C636A' }}>{format.toUpperCase()} · pedidos, transações e disputas</span>
                </div>
              </div>
              <div style={{ height: 6, borderRadius: 100, background: '#E6DDC9', overflow: 'hidden' }}>
                <div style={{ width: `${Math.min(progress, 100)}%`, height: '100%', background: '#14A8A0', borderRadius: 100, transition: 'width 0.3s ease' }} />
              </div>
            </>
          ) : done ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, padding: '20px 0' }}>
              <div style={{ width: 60, height: 60, borderRadius: 20, background: '#DDF0EC', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="#15756E" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg>
              </div>
              <span style={{ fontSize: 16, fontWeight: 800, color: '#0E2A33' }}>Relatório pronto!</span>
              <span style={{ fontSize: 13, color: '#4C636A' }}>O download começou automaticamente.</span>
              <button onClick={() => setDone(false)} style={{ height: 44, padding: '0 24px', border: '1.5px solid #E6DDC9', borderRadius: 100, background: 'transparent', color: '#4C636A', fontWeight: 700, fontSize: 14, cursor: 'pointer' }}>Gerar outro</button>
            </div>
          ) : (
            <>
              <span style={{ fontSize: 16, fontWeight: 800, color: '#0E2A33', letterSpacing: '-0.01em' }}>Exportar relatório</span>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {/* CSV option */}
                <div onClick={() => setFormat('csv')} style={{ display: 'flex', alignItems: 'center', gap: 12, background: format === 'csv' ? '#F3ECDC' : '#fff', border: `2px solid ${format === 'csv' ? '#14A8A0' : '#E6DDC9'}`, borderRadius: 12, padding: 13, cursor: 'pointer' }}>
                  <div style={{ width: 40, height: 40, borderRadius: 11, background: '#DDF0EC', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#15756E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V9z"/><polyline points="14 3 14 9 20 9"/></svg>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0E2A33' }}>CSV</div>
                    <div style={{ fontSize: 12, color: '#4C636A' }}>Listagens (pedidos, transações, disputas)</div>
                  </div>
                  {format === 'csv' ? <div style={{ width: 20, height: 20, borderRadius: '50%', background: '#14A8A0', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg></div> : <div style={{ width: 20, height: 20, borderRadius: '50%', border: '2px solid #DCD2BC', flexShrink: 0 }} />}
                </div>
                {/* PDF option */}
                <div onClick={() => setFormat('pdf')} style={{ display: 'flex', alignItems: 'center', gap: 12, background: format === 'pdf' ? '#F3ECDC' : '#fff', border: `2px solid ${format === 'pdf' ? '#14A8A0' : '#E6DDC9'}`, borderRadius: 12, padding: 13, cursor: 'pointer' }}>
                  <div style={{ width: 40, height: 40, borderRadius: 11, background: '#F7E3D6', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#C2572A" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 3H6a2 2 0 00-2 2v14a2 2 0 002 2h12a2 2 0 002-2V9z"/><polyline points="14 3 14 9 20 9"/></svg>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 700, color: '#0E2A33' }}>PDF</div>
                    <div style={{ fontSize: 12, color: '#4C636A' }}>Resumo de métricas</div>
                  </div>
                  {format === 'pdf' ? <div style={{ width: 20, height: 20, borderRadius: '50%', background: '#14A8A0', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="5 12 10 17 19 7"/></svg></div> : <div style={{ width: 20, height: 20, borderRadius: '50%', border: '2px solid #DCD2BC', flexShrink: 0 }} />}
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#E2EEF2', borderRadius: 10, padding: '9px 12px' }}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#15596E" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="5" width="18" height="16" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/></svg>
                <span style={{ fontSize: 12, color: '#15596E', fontWeight: 600 }}>Respeita o período e bairro selecionados.</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 7 }}>
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#8A989B" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: 2 }}><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 018 0v3"/></svg>
                <span style={{ fontSize: 12, lineHeight: 1.45, color: '#8A989B' }}>Os relatórios não incluem dados pessoais sensíveis (CPF).</span>
              </div>
              <button onClick={generate} style={{ width: '100%', height: 48, border: 'none', borderRadius: 100, background: '#14A8A0', color: '#fff', fontWeight: 700, fontSize: 15, cursor: 'pointer', boxShadow: '0 14px 24px -14px rgba(20,168,160,.85)' }}>Gerar relatório</button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
