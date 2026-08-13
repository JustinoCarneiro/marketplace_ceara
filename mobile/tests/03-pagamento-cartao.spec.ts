import { test, expect } from '@playwright/test';
import { registerCliente, registerPrestador, login } from './helpers/auth';

// Fluxo dedicado a pagamento com Cartão — antes desta suíte, PaymentCardScreen nunca tinha
// sido exercitada por E2E nenhum (só Pix, em 02-fluxo-pedido-completo.spec.ts). O botão
// "Pagar" chamava só um setTimeout fake e navegava pra "Pagamento retido" sem checar nada —
// corrigido pra pollar a confirmação real (mesmo helper pollPaymentConfirmed do Pix).
const ts = Date.now();

function fakeCpf(seed: number): string {
  const d = String(seed).slice(-9).padStart(9, '0');
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-00`;
}

const CLIENTE = { nome: 'Rita Cartao', email: `rita-cartao-${ts}@onda.dev`, senha: 'senha1234' };
const PRESTADOR = {
  nome: 'Caio Cartao', cpf: fakeCpf(ts + 1),
  email: `caio-cartao-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Hidráulica',
};
const DESCRICAO = 'Vazamento embaixo da pia da cozinha, água acumulando no armário.';
const CLIENTE_CPF = fakeCpf(ts);

test('setup: cadastra cliente e prestador do fluxo de cartão', async ({ browser }) => {
  const p1 = await browser.newPage();
  await registerCliente(p1, CLIENTE);
  await p1.close();

  const p2 = await browser.newPage();
  await registerPrestador(p2, PRESTADOR);
  await p2.close();
});

test('cliente cria pedido, prestador propõe, cliente aceita e chega no pagamento', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Criar pedido', { exact: true }).click();
  await expect(page.getByText('Novo pedido')).toBeVisible({ timeout: 8000 });
  await page.getByText('Hidráulica', { exact: true }).last().click();
  await page.getByPlaceholder(/A tomada da cozinha solta faísca/).fill(DESCRICAO);
  await page.getByText('Continuar', { exact: true }).click();
  await expect(page.getByText('Confirmar e publicar pedido')).toBeVisible({ timeout: 15000 });
  await page.getByText('Confirmar e publicar pedido', { exact: true }).click();
  await expect(page.getByText('Pedido criado!')).toBeVisible({ timeout: 10000 });
});

test('prestador envia proposta pro pedido de cartão', async ({ page }) => {
  await login(page, PRESTADOR.email, PRESTADOR.senha);
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-propor').first().click();
  await expect(page.getByText('SEU VALOR')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-valor').fill('220');
  await page.getByTestId('btn-enviar-proposta').click();
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
});

test('cliente paga com cartão e vê a confirmação real (não mais fake)', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  await expect(page.getByText('PROPOSTO', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-ver-propostas').click();
  await expect(page.getByText('Aceitar proposta').first()).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-aceitar-proposta').first().click();

  await expect(page.getByTestId('btn-pagar')).toBeVisible({ timeout: 8000 });
  await page.getByText('Cartão de crédito', { exact: true }).click();
  await page.getByTestId('btn-pagar').click();

  await expect(page.getByText('Confirme sua identidade')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-cpf').fill(CLIENTE_CPF);
  await page.getByTestId('btn-confirmar-cpf').click();

  // PaymentCardScreen real: campo de nome existe agora (não existia antes desta correção).
  await expect(page.getByText('NOME NO CARTÃO')).toBeVisible({ timeout: 8000 });
  await page.getByPlaceholder('LÚCIA M ALVES').fill('Rita Cartao');
  await page.getByPlaceholder('5102 4830 1192 4821').fill('4111111111111111');
  await page.getByPlaceholder('08/29').fill('1230');
  await page.getByPlaceholder('•••').fill('123');

  await page.getByText(/Pagar R\$/).click();

  // Antes: navegava direto pra "Pagamento retido" sem nenhuma checagem — agora precisa
  // esperar o poll confirmar de verdade contra o backend (pode levar >1 ciclo).
  await expect(page.getByText('Confirmando pagamento…')).toBeVisible({ timeout: 5000 }).catch(() => {});
  await expect(page.getByText('Pagamento retido com segurança')).toBeVisible({ timeout: 20000 });

  // EscrowConfirmedScreen real: nome do prestador vem do backend, não mais "José Wagner" fixo.
  // .last(): react-native-screens mantém a PaymentCard montada (porém hidden) por trás e o nome
  // aparece nas duas telas — a de cima (EscrowConfirmed) é a que vem depois no DOM. Sem isso o
  // strict mode reprova por 2 matches; com .first() casa justamente a escondida. Mesmo motivo
  // do .last() no teste 02.
  await expect(page.getByText('Caio Cartao').last()).toBeVisible({ timeout: 8000 });
});
