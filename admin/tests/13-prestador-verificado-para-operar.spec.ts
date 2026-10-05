import { test, expect, request, APIRequestContext } from '@playwright/test';
import { cpfNovo } from './helpers/cpf';

/**
 * Só prestador VERIFICADO opera (ProviderVerificationGuard). Até aqui a "aprovação manual" do admin
 * só gravava um status: ele filtrava a busca por proximidade, mas prestador em verificação,
 * reprovado ou suspenso continuava mandando proposta e, aceito, recebendo.
 *
 * Cada teste usa prestador e pedido NOVOS — nada depende do estado deixado por outros testes (o
 * prestador do seed é alvo de moderação nos testes do painel).
 */
const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';
const ts = Date.now();
let contador = 0;


async function login(ctx: APIRequestContext, email: string, senha: string) {
  const r = await ctx.post(`${API}/auth/login`, { data: { email, senha } });
  expect(r.ok(), `login de ${email} falhou — backend no ar com profile seed?`).toBeTruthy();
  return (await r.json()) as { accessToken: string; userId: string };
}

const auth = (token: string) => ({ Authorization: `Bearer ${token}` });

async function novoPrestador(ctx: APIRequestContext) {
  const n = ++contador;
  const r = await ctx.post(`${API}/auth/register/provider`, {
    data: {
      nome: `Prestador Portao ${n}`, email: `portao-${ts}-${n}@teste.com`, senha: 'Senha@123',
      cpf: cpfNovo(), categoria: 'eletrica', bio: 'Prestador criado pelo teste do portão.',
      aceitouTermos: true,
    },
  });
  expect(r.status()).toBe(201);
  return (await r.json()) as { accessToken: string; userId: string };
}

async function novoPedido(ctx: APIRequestContext) {
  const n = ++contador;
  const cadastro = await ctx.post(`${API}/auth/register/client`, {
    data: { nome: `Cliente Portao ${n}`, email: `cliente-portao-${ts}-${n}@teste.com`, senha: 'Senha@123', aceitouTermos: true },
  });
  expect(cadastro.ok()).toBeTruthy();
  const { accessToken } = await cadastro.json();
  const pedido = await ctx.post(`${API}/service-requests`, {
    headers: { ...auth(accessToken), 'X-Idempotency-Key': `portao-${ts}-${n}` },
    data: { categoria: 'Elétrica', descricao: 'Tomada sem funcionar no quarto', lat: -3.73, lng: -38.52 },
  });
  expect(pedido.status()).toBe(201);
  return { tokenCliente: accessToken as string, requestId: (await pedido.json()).id as string };
}

function propor(ctx: APIRequestContext, tokenPrestador: string, requestId: string) {
  return ctx.post(`${API}/service-requests/${requestId}/proposals`, {
    headers: auth(tokenPrestador),
    data: { valor: 250, prazoDias: 1, horarioProposto: new Date(Date.now() + 2 * 86_400_000).toISOString() },
  });
}

async function moderar(ctx: APIRequestContext, tokenAdmin: string, userId: string, action: 'APROVAR' | 'REPROVAR' | 'SUSPENDER') {
  const r = await ctx.post(`${API}/admin/providers/${userId}/moderate`, {
    headers: auth(tokenAdmin), data: { action },
  });
  expect(r.ok(), `moderação ${action} falhou (HTTP ${r.status()})`).toBeTruthy();
}

async function recusadaComMotivo(r: Awaited<ReturnType<typeof propor>>, trecho: string) {
  expect(r.status()).toBe(422);
  const corpo = await r.json();
  expect(corpo.code).toBe('PROVIDER_NOT_VERIFIED');
  expect(corpo.message).toContain(trecho);
}

test.describe('Prestador verificado para operar', () => {
  test('em verificação, reprovado ou suspenso não propõe — cada um com o seu motivo; aprovado, propõe', async () => {
    const ctx = await request.newContext();
    const { accessToken: tokenAdmin } = await login(ctx, 'admin@onda.com', 'admin123');
    const { accessToken: tokenPrestador, userId } = await novoPrestador(ctx);
    const { requestId } = await novoPedido(ctx);

    // recém-cadastrado: EM_VERIFICACAO
    await recusadaComMotivo(await propor(ctx, tokenPrestador, requestId), 'em verificação');

    await moderar(ctx, tokenAdmin, userId, 'APROVAR');
    expect((await propor(ctx, tokenPrestador, requestId)).status()).toBe(201);

    await moderar(ctx, tokenAdmin, userId, 'SUSPENDER');
    await recusadaComMotivo(await propor(ctx, tokenPrestador, requestId), 'suspenso');

    await moderar(ctx, tokenAdmin, userId, 'REPROVAR');
    await recusadaComMotivo(await propor(ctx, tokenPrestador, requestId), 'não foi aprovado');
  });

  test('o prestador verificado do seed continua propondo (a regra não bloqueia todo mundo)', async () => {
    const ctx = await request.newContext();
    const { accessToken: tokenAna } = await login(ctx, 'ana.eletricista@teste.com', 'Senha@123');
    const { requestId } = await novoPedido(ctx);

    expect((await propor(ctx, tokenAna, requestId)).status()).toBe(201);
  });

  test('o cliente não aceita proposta de quem foi suspenso depois de propor; reaprovado, aceita', async () => {
    const ctx = await request.newContext();
    const { accessToken: tokenAdmin } = await login(ctx, 'admin@onda.com', 'admin123');
    const { accessToken: tokenPrestador, userId } = await novoPrestador(ctx);
    const { tokenCliente, requestId } = await novoPedido(ctx);

    await moderar(ctx, tokenAdmin, userId, 'APROVAR');
    const proposta = await propor(ctx, tokenPrestador, requestId);
    expect(proposta.status()).toBe(201);
    const propostaId = (await proposta.json()).id as string;

    // O admin suspende entre a proposta e o aceite: o cliente não pode pagar quem já foi barrado.
    await moderar(ctx, tokenAdmin, userId, 'SUSPENDER');
    const recusa = await ctx.put(`${API}/proposals/${propostaId}/accept`, { headers: auth(tokenCliente) });
    expect(recusa.status()).toBe(422);
    const corpo = await recusa.json();
    expect(corpo.code).toBe('PROVIDER_NOT_VERIFIED');
    expect(corpo.message).toContain('Escolha outra proposta');

    // A recusa não consome a proposta nem mexe no pedido: ela continua ATIVA.
    const lista = await (await ctx.get(`${API}/service-requests/${requestId}/proposals`, { headers: auth(tokenCliente) })).json();
    expect(lista[0].status).toBe('ATIVA');

    await moderar(ctx, tokenAdmin, userId, 'APROVAR');
    const aceite = await ctx.put(`${API}/proposals/${propostaId}/accept`, { headers: auth(tokenCliente) });
    expect(aceite.status()).toBe(200);
    expect((await aceite.json()).status).toBe('ACEITA');
  });
});
