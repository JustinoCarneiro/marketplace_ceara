import { test, expect } from '@playwright/test';
import { filtrosDoPainel } from '../src/utils/periodo';

// Sem browser, só a conta. O dashboard e as exportações CSV/PDF usam este mesmo ajudante, então
// o arquivo exportado cobre exatamente o que a tela mostra (US23/US29). O dia é o de Fortaleza
// (UTC-3), o mesmo fuso em que o backend interpreta `de`/`ate`.

const q = (dias: number | null, agora: string, bairro = '') =>
  new URLSearchParams(filtrosDoPainel({ dias, bairro, agora: new Date(agora) }));

test.describe('filtrosDoPainel', () => {
  test('sem janela e sem bairro, não filtra nada', () => {
    expect(filtrosDoPainel({ dias: null, bairro: '' })).toBe('');
  });

  test('últimos 7 dias ao meio-dia: de = hoje − 7, ate = hoje', () => {
    const p = q(7, '2026-10-01T15:00:00Z'); // 12:00 em Fortaleza
    expect(p.get('de')).toBe('2026-09-24');
    expect(p.get('ate')).toBe('2026-10-01');
  });

  test('à noite (depois das 21h locais) o dia continua sendo o de Fortaleza, não o UTC', () => {
    // 22:30 do dia 01/10 em Fortaleza já é 02/10 em UTC. Com toISOString().slice(0, 10) a janela
    // virava 25/09 → 02/10: perdia o dia 24 e incluía o dia 02, que ainda nem começou.
    const p = q(7, '2026-10-02T01:30:00Z');
    expect(p.get('ate')).toBe('2026-10-01');
    expect(p.get('de')).toBe('2026-09-24');
  });

  test('a virada do dia acontece à meia-noite de Fortaleza (03:00Z)', () => {
    expect(q(7, '2026-10-01T02:59:59Z').get('ate')).toBe('2026-09-30');
    expect(q(7, '2026-10-01T03:00:00Z').get('ate')).toBe('2026-10-01');
  });

  test('janela atravessa virada de mês e de ano', () => {
    expect(q(30, '2026-01-10T15:00:00Z').get('de')).toBe('2025-12-11');
    expect(q(90, '2026-03-01T15:00:00Z').get('de')).toBe('2025-12-01');
  });

  test('bairro vai junto, codificado, depois do período', () => {
    const qs = filtrosDoPainel({ dias: 7, bairro: 'São Gerardo', agora: new Date('2026-10-01T15:00:00Z') });
    expect(qs).toBe('de=2026-09-24&ate=2026-10-01&bairro=S%C3%A3o+Gerardo');
    expect(new URLSearchParams(qs).get('bairro')).toBe('São Gerardo');
  });

  test('só bairro, sem janela: não manda datas', () => {
    const p = q(null, '2026-10-01T15:00:00Z', 'Aldeota');
    expect(p.has('de')).toBe(false);
    expect(p.has('ate')).toBe(false);
    expect(p.get('bairro')).toBe('Aldeota');
  });
});
