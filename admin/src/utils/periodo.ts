/**
 * Filtros do painel (período e bairro) em um lugar só. O dashboard e as exportações CSV/PDF
 * usam esta mesma conta, para o arquivo cobrir exatamente o que a tela mostra (US23/US29).
 */

/** Janelas oferecidas no filtro; null = histórico completo (o backend aceita sem datas). */
export const PERIODOS = [
  { dias: 7,    label: 'Últimos 7 dias' },
  { dias: 30,   label: 'Últimos 30 dias' },
  { dias: 90,   label: 'Últimos 90 dias' },
  { dias: null, label: 'Todo o período' },
] as const;

/**
 * Fuso em que o backend interpreta `de`/`ate` (AdminReportService.ZONA_NEGOCIO). O dia certo é o
 * de quem opera o painel em Fortaleza — não o do navegador nem o UTC: `toISOString().slice(0, 10)`
 * devolve o dia UTC, que das 21h à meia-noite locais já é o dia seguinte, e "últimos 7 dias"
 * perdia um dia nesse horário. Fortaleza não tem horário de verão, então um dia são 24 h exatas.
 */
const ZONA_NEGOCIO = 'America/Fortaleza';
const MS_POR_DIA = 24 * 60 * 60 * 1000;

/** AAAA-MM-DD do instante no fuso do negócio (o locale en-CA formata assim). */
function diaDoNegocio(instante: Date): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA_NEGOCIO }).format(instante);
}

/**
 * Query string (sem o `?`) dos filtros aplicados: `de`/`ate` quando há janela de dias e `bairro`
 * quando escolhido. String vazia = sem filtro.
 */
export function filtrosDoPainel(
  { dias, bairro, agora = new Date() }: { dias: number | null; bairro: string; agora?: Date },
): string {
  const params = new URLSearchParams();
  if (dias !== null) {
    params.set('de', diaDoNegocio(new Date(agora.getTime() - dias * MS_POR_DIA)));
    params.set('ate', diaDoNegocio(agora));
  }
  if (bairro) params.set('bairro', bairro);
  return params.toString();
}
