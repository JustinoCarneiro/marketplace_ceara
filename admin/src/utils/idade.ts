const MS_POR_HORA = 3_600_000;

/** Horas inteiras entre o instante {@code iso} e {@code agora} (ms desde a época). */
export function horasDesde(iso: string, agora: number): number {
  return Math.floor((agora - new Date(iso).getTime()) / MS_POR_HORA);
}

/** Idade legível de um registro: "5h" até um dia, depois "2d 3h". */
export function idadeDe(iso: string, agora: number): string {
  const h = horasDesde(iso, agora);
  if (h < 24) return `${h}h`;
  return `${Math.floor(h / 24)}d ${h % 24}h`;
}
