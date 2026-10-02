import { test, expect } from '@playwright/test';
import { codigoCompleto, emailValido, formatarCodigo } from '../src/api/passwordReset';

// Sem browser, só as contas que a tela de nova senha usa (US35). O backend aceita o código como o
// usuário digitar (minúsculas, hífen, O no lugar do zero); isto só mostra o que foi digitado do
// jeito do e-mail e decide quando o botão pode ir ao servidor.

test.describe('formatarCodigo', () => {
  const casos: [digitado: string, mostrado: string][] = [
    ['',            ''],
    ['abcd',        'ABCD'],
    ['abcd2',       'ABCD-2'],
    ['abcd2345',    'ABCD-2345'],
    ['ABCD-2345',   'ABCD-2345'],          // colar o código do e-mail como veio
    [' a b c d 2 3 4 5 ', 'ABCD-2345'],    // espaços (copiar/colar com sobra)
    ['abcd#23$45',  'ABCD-2345'],          // símbolos somem
    ['abcd23456789', 'ABCD-2345'],         // passou de 8: corta
  ];
  for (const [digitado, mostrado] of casos) {
    test(`"${digitado}" → "${mostrado}"`, () => {
      expect(formatarCodigo(digitado)).toBe(mostrado);
    });
  }
});

test.describe('codigoCompleto', () => {
  test('só vale com os 8 caracteres (o hífen não conta)', () => {
    expect(codigoCompleto('ABCD-2345')).toBe(true);
    expect(codigoCompleto('ABCD2345')).toBe(true);
    expect(codigoCompleto('ABCD-234')).toBe(false);
    expect(codigoCompleto('')).toBe(false);
  });
});

test.describe('emailValido', () => {
  test('aceita e-mail com cara de e-mail e recusa o resto', () => {
    expect(emailValido('ana@example.com')).toBe(true);
    expect(emailValido('  ana@example.com  ')).toBe(true);
    for (const ruim of ['', 'ana', 'ana@', '@example.com', 'ana@example', 'a na@example.com']) {
      expect(emailValido(ruim), ruim).toBe(false);
    }
  });
});
