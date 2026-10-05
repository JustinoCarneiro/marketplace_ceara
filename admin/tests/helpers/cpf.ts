/**
 * CPF fictício com os dígitos verificadores CERTOS e diferente a cada chamada. O backend valida os dígitos e recusa CPF
 * repetido (uma pessoa = um CPF): um CPF fixo colide na segunda execução contra o mesmo banco, e um número "só no
 * formato" é recusado como inválido.
 */
let contador = 0;

export function cpfNovo(): string {
  const base = String(Date.now() + ++contador).slice(-9).padStart(9, '0');
  const d = base.split('').map(Number);
  for (const n of [9, 10]) {
    const soma = d.slice(0, n).reduce((acc, x, i) => acc + x * (n + 1 - i), 0);
    d.push(((soma * 10) % 11) % 10);
  }
  const t = d.join('');
  return `${t.slice(0, 3)}.${t.slice(3, 6)}.${t.slice(6, 9)}-${t.slice(9)}`;
}
