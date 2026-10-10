import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';

// Pedido sem prestador (PENDENTE/PROPOSTO): o cliente tinha como cancelar só ACEITO e EM_ANDAMENTO — um pedido que
// ninguém atendia não tinha saída. Agora ele cancela sozinho (sem reembolso: ainda não há dinheiro).
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem isso o worker novo
// reavalia `ts` e o login procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();
const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';
const CLIENTE = { nome: 'Cida Cancela', email: `cida-cancela-${ts}@onda.dev`, senha: 'senha1234' };
let tokenCliente = '';
let pedidoId = '';

test('setup: cliente novo com um pedido PENDENTE, pela API', async () => {
  const cadastro = await fetch(`${API}/auth/register/client`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...CLIENTE, aceitouTermos: true }),
  });
  expect(cadastro.status, 'cadastro pela API').toBe(201);
  tokenCliente = (await cadastro.json()).accessToken;

  const pedido = await fetch(`${API}/service-requests`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokenCliente}`, 'X-Idempotency-Key': `cancela-${ts}` },
    body: JSON.stringify({ categoria: 'Elétrica', descricao: 'Tomada da varanda sem energia', lat: -3.7319, lng: -38.5267 }),
  });
  expect(pedido.status, 'criação do pedido').toBe(201);
  pedidoId = (await pedido.json()).id;
});

test('o cliente cancela o pedido pendente: a confirmação não fala de reembolso e o pedido vira CANCELADO', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  // o estado aparece também na lista que fica empilhada atrás do detalhe: o detalhe é o último
  await expect(page.getByText('PENDENTE', { exact: true }).last()).toBeVisible({ timeout: 8000 });

  // No web o app usa window.confirm (Alert.alert não faz nada lá); no celular, o Alert nativo.
  let mensagem = '';
  page.once('dialog', async d => { mensagem = d.message(); await d.accept(); });
  await page.getByTestId('btn-cancelar-pedido').click();

  await expect(page.getByText('CANCELADO', { exact: true }).last()).toBeVisible({ timeout: 8000 });
  await expect(page.getByTestId('btn-cancelar-pedido')).toHaveCount(0);   // cancelado: sem segundo cancelamento
  expect(mensagem).toContain('Não dá para desfazer');
  expect(mensagem, 'ainda não há dinheiro retido: falar de reembolso seria mentira').not.toMatch(/reembols|valor retido/i);

  // e o backend concorda
  const res = await fetch(`${API}/service-requests/${pedidoId}`, { headers: { Authorization: `Bearer ${tokenCliente}` } });
  expect((await res.json()).status).toBe('CANCELADO');
});
