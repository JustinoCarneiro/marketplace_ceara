import { API_BASE } from './config';

/**
 * Percentual de comissão vigente na plataforma (0.10 = 10%), vindo do backend — a mesma
 * configuração que a cobrança aplica. Devolve `null` se não deu pra saber (rede, backend fora,
 * resposta fora do formato): quem chama esconde a informação em vez de chutar um número, já
 * que nada do fluxo depende dela — o backend é quem aplica a comissão de verdade.
 */
export async function fetchPercentualComissao(token: string | null): Promise<number | null> {
  try {
    const res = await fetch(`${API_BASE}/payments/comissao`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return null;
    const data = await res.json();
    const percentual = Number(data?.percentualComissao);
    return Number.isFinite(percentual) && percentual >= 0 && percentual < 1 ? percentual : null;
  } catch {
    return null;
  }
}

/**
 * Quanto o prestador recebe de uma proposta de `valor` reais depois da comissão. O backend
 * calcula valor × percentual e o banco guarda em centavos (NUMERIC(12,2), arredondando o
 * meio pra cima); repetir a conta em ponto flutuante erra 1 centavo em alguns valores. Aqui
 * é aritmética inteira: centavos × pontos-base (0.10 → 1000), com o meio arredondado pra cima.
 */
export function valorAposComissao(valor: number, percentual: number): number {
  const centavos = Math.round(valor * 100);
  const pontosBase = Math.round(percentual * 10000);
  const comissao = Math.floor((centavos * pontosBase + 5000) / 10000);
  return (centavos - comissao) / 100;
}
