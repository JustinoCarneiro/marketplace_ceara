import { test, expect } from '@playwright/test';
import { login, fakeCpf } from './helpers/auth';
import { aprovarPrestador } from './helpers/admin';

// Conta única com papéis: a MESMA pessoa é cliente e prestador numa conta só (modelo Uber/Airbnb). O prestador que precisa
// de um eletricista em casa alterna para o modo cliente e contrata — sem segunda conta, sem outro e-mail, sem confirmar o
// CPF de novo; e o cliente que quer prestar serviço usa "Quero ser prestador" no Perfil. Antes, o CPF único entre papéis
// forçava duas contas (ou impedia o prestador de contratar). O backend tem E2E real; aqui se prova o que o USUÁRIO vê.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem isso o worker novo reavalia
// `ts` e o login procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();
const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';
const SENHA = 'senha1234';

const EDU = { nome: 'Edu Eletricista', email: `edu-unica-${ts}@onda.dev`, cpf: fakeCpf(ts + 20) };
const DUDA = { nome: 'Duda Dual', email: `duda-unica-${ts}@onda.dev`, cpf: fakeCpf(ts + 21) };
const ANA = { nome: 'Ana Cliente', email: `ana-unica-${ts}@onda.dev` };
const BETO = { nome: 'Beto Cliente', email: `beto-unica-${ts}@onda.dev` };
const DESCRICAO_DA_DUDA = 'Trocar a fiação da casa da Duda na Aldeota';

async function post(path: string, body: object, token?: string, headers: Record<string, string> = {}) {
  return fetch(`${API}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: JSON.stringify(body),
  });
}

async function cadastrarPrestador(p: { nome: string; email: string; cpf: string }, aprovar: boolean) {
  const res = await post('/auth/register/provider', { ...p, senha: SENHA, categoria: 'Elétrica', aceitouTermos: true });
  expect(res.status, `cadastro do prestador ${p.email}`).toBe(201);
  if (aprovar) await aprovarPrestador(p.email, SENHA);   // só prestador aprovado propõe
  return (await res.json()) as { accessToken: string; refreshToken: string; papeis: string[] };
}

test('setup: a Duda é prestadora E cliente na mesma conta; o Edu faz o serviço; a Ana é só cliente', async () => {
  const edu = await cadastrarPrestador(EDU, true);
  const duda = await cadastrarPrestador(DUDA, true);
  // o cadastro de prestador já dá os dois papéis: todo prestador também contrata
  expect(duda.papeis).toEqual(['ROLE_CLIENT', 'ROLE_PROVIDER']);

  // a Duda abre um pedido pela sessão de CLIENTE da própria conta, e o Edu propõe
  const comoCliente = await post('/auth/switch-role', { papel: 'ROLE_CLIENT', refreshToken: duda.refreshToken }, duda.accessToken);
  expect(comoCliente.status, 'alternar para cliente pela API').toBe(200);
  const tokenCliente = (await comoCliente.json()).accessToken as string;
  const pedido = await post('/service-requests', {
    categoria: 'Elétrica', descricao: DESCRICAO_DA_DUDA, lat: -3.7319, lng: -38.5267,
  }, tokenCliente, { 'X-Idempotency-Key': `unica-${ts}` });
  expect(pedido.status, 'criação do pedido').toBe(201);
  const pedidoId = (await pedido.json()).id as string;
  const proposta = await post(`/service-requests/${pedidoId}/proposals`, {
    valor: 150, prazoDias: 1, horarioProposto: new Date(Date.now() + 2 * 24 * 3600 * 1000).toISOString(),
  }, edu.accessToken);
  expect(proposta.status, 'proposta do Edu').toBe(201);

  const ana = await post('/auth/register/client', { ...ANA, senha: SENHA, aceitouTermos: true });
  expect(ana.status, 'cadastro da Ana').toBe(201);
  const beto = await post('/auth/register/client', { ...BETO, senha: SENHA, aceitouTermos: true });
  expect(beto.status, 'cadastro do Beto').toBe(201);
});

test('a prestadora alterna para o modo cliente na MESMA conta e contrata pagando direto, sem confirmar o CPF de novo', async ({ page }) => {
  await login(page, DUDA.email, SENHA);
  // entra no modo prestador (o papel principal do cadastro) e NÃO vê o pedido que ela mesma abriu como cliente
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText(DESCRICAO_DA_DUDA)).toHaveCount(0);

  await page.getByText('Perfil', { exact: true }).click();
  await expect(page.getByText('Prestador', { exact: true })).toBeVisible();
  await page.getByTestId('btn-alternar-papel').click();

  // agora é cliente: mesma conta, outra navegação
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  await expect(page.getByText('PROPOSTO', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-ver-propostas').click();
  await page.getByTestId('btn-aceitar-proposta').first().click();
  await expect(page.getByTestId('btn-pagar')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-pagar').click();

  // o CPF já foi confirmado no cadastro de prestador: nenhum "Confirme sua identidade", vai direto ao Pix
  await expect(page.getByTestId('btn-paguei')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Confirme sua identidade')).toHaveCount(0);
});

test('o cliente vira prestador pela própria conta ("Quero ser prestador"), cai em verificação e alterna de volta', async ({ page }) => {
  await login(page, ANA.email, SENHA);
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Perfil', { exact: true }).click();
  await expect(page.getByTestId('btn-alternar-papel')).toHaveCount(0);   // ainda não é prestadora: nada a alternar
  await page.getByTestId('btn-quero-ser-prestador').click();

  await expect(page.getByText(/Você continua com a mesma conta/)).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-cpf-prestador').fill(fakeCpf(ts + 22));
  // react-native-screens mantém a Home montada por trás: "Elétrica" aparece nos dois, a tela de cima vem depois no DOM
  await page.getByText('Elétrica', { exact: true }).last().click();
  await page.getByTestId('checkbox-termos-prestador').click();
  await page.getByTestId('btn-enviar-prestador').click();

  // já em modo prestador (a verificação do cadastro é separada): o Perfil oferece voltar ao modo cliente
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 10000 });
  await page.getByText('Perfil', { exact: true }).click();
  await page.getByTestId('btn-alternar-papel').click();
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Perfil', { exact: true }).click();
  await expect(page.getByTestId('btn-quero-ser-prestador')).toHaveCount(0);   // já é prestadora: não repete o cadastro
});

test('"Quero ser prestador" com o CPF de outra conta é recusado com a mensagem do servidor e segue no modo cliente', async ({ page }) => {
  await login(page, BETO.email, SENHA);
  await page.getByText('Perfil', { exact: true }).click();
  await page.getByTestId('btn-quero-ser-prestador').click();
  await page.getByTestId('input-cpf-prestador').fill(DUDA.cpf);   // o CPF da Duda (uma pessoa = um CPF)
  // react-native-screens mantém a Home montada por trás: "Elétrica" aparece nos dois, a tela de cima vem depois no DOM
  await page.getByText('Elétrica', { exact: true }).last().click();
  await page.getByTestId('checkbox-termos-prestador').click();
  await page.getByTestId('btn-enviar-prestador').click();

  await expect(page.getByTestId('erro-tornar-prestador')).toContainText('já está vinculado', { timeout: 8000 });
  await expect(page.getByText('Pedidos disponíveis')).toHaveCount(0);   // não virou prestador
});
