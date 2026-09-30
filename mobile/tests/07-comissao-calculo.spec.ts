import { test, expect } from '@playwright/test';
import { valorAposComissao } from '../src/api/comissao';

// "Você recebe após comissão" (SendProposalScreen) tem que bater no centavo com o repasse
// real: o backend calcula valor × percentual e o Postgres guarda em NUMERIC(12,2), que
// arredonda o meio pra cima. Sem browser — só a conta. Os casos incluem os que ponto
// flutuante erra (10,05 × 0,9 dá 9,045000…02 e vira 9,05; o repasse real é 9,04).
const casos: [valor: number, percentual: number, recebe: number][] = [
  [150, 0.10, 135],
  [250, 0.25, 187.5],
  [10.05, 0.10, 9.04],       // comissão 1,005 → 1,01: o meio sobe
  [0.05, 0.10, 0.04],        // 0,005 → 0,01
  [0.04, 0.10, 0.04],        // 0,004 → 0,00
  [99.99, 0.15, 84.99],      // 14,9985 → 15,00
  [1234.56, 0.10, 1111.1],   // 123,456 → 123,46
  [100, 0, 100],             // sem comissão
];

for (const [valor, percentual, recebe] of casos) {
  test(`R$ ${valor} com comissão de ${Math.round(percentual * 100)}% → prestador recebe R$ ${recebe}`, () => {
    expect(valorAposComissao(valor, percentual)).toBe(recebe);
  });
}
