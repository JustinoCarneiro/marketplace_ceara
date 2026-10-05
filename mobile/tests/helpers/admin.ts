/// <reference types="node" />
import { expect } from '@playwright/test';

// A API real, a mesma que o app usa nos testes (o backend sobe com o profile seed no CI e aqui).
const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';

// Admin do profile seed — o mesmo que os testes do painel usam.
const ADMIN = { email: 'admin@onda.com', senha: 'admin123' };

async function post(
  caminho: string,
  corpo: unknown,
  extra: { token?: string; headers?: Record<string, string> } = {},
) {
  return fetch(`${API}${caminho}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(extra.token ? { Authorization: `Bearer ${extra.token}` } : {}),
      ...extra.headers,
    },
    body: JSON.stringify(corpo),
  });
}

/**
 * Só prestador VERIFICADO envia proposta (ProviderVerificationGuard). O cadastro pela interface deixa
 * a conta EM_VERIFICACAO, então os fluxos que mandam proposta chamam isto logo depois de cadastrar o
 * prestador: aprova pela API do admin, o mesmo caminho do botão "Verificar" do painel.
 */
export async function aprovarPrestador(email: string, senha: string) {
  const loginPrestador = await post('/auth/login', { email, senha });
  expect(loginPrestador.ok, `login do prestador ${email} falhou (HTTP ${loginPrestador.status})`).toBeTruthy();
  const { userId } = await loginPrestador.json();

  const loginAdmin = await post('/auth/login', ADMIN);
  expect(loginAdmin.ok, 'login do admin falhou — backend no ar com profile seed?').toBeTruthy();
  const { accessToken } = await loginAdmin.json();

  const aprovado = await post(`/admin/providers/${userId}/verify`, {}, { token: accessToken });
  expect(aprovado.ok, `aprovação do prestador falhou (HTTP ${aprovado.status})`).toBeTruthy();
}

/**
 * Cria, pela API, um cliente novo com um pedido PENDENTE: é o primeiro da lista "Pedidos
 * disponíveis" do prestador (ordenada do mais novo), sem passar pelo fluxo de criação na tela.
 */
export async function criarPedidoPendente(sufixo: number | string) {
  const cadastro = await post('/auth/register/client', {
    nome: 'Cliente Pedido', email: `cliente-pedido-${sufixo}@onda.dev`,
    senha: 'senha1234', aceitouTermos: true,
  });
  expect(cadastro.ok, `cadastro do cliente falhou (HTTP ${cadastro.status})`).toBeTruthy();
  const { accessToken } = await cadastro.json();

  const pedido = await post('/service-requests', {
    categoria: 'Elétrica', descricao: 'Tomada da sala sem funcionar, preciso de um eletricista.',
    lat: -3.7319, lng: -38.5267,
  }, { token: accessToken, headers: { 'X-Idempotency-Key': `pedido-${sufixo}` } });
  expect(pedido.status, 'criação do pedido pela API').toBe(201);
}

/**
 * Deixa um serviço ACEITO entre o prestador (já aprovado) e um cliente novo, tudo pela API: cadastro do
 * cliente, pedido, proposta e aceite. É o estado em que nenhum dos dois pode excluir a conta (US36).
 */
export async function criarServicoAceito(sufixo: number | string, prestador: { email: string; senha: string }) {
  const cadastro = await post('/auth/register/client', {
    nome: 'Cliente Contratante', email: `cliente-contratante-${sufixo}@onda.dev`,
    senha: 'senha1234', aceitouTermos: true,
  });
  expect(cadastro.ok, `cadastro do cliente falhou (HTTP ${cadastro.status})`).toBeTruthy();
  const tokenCliente = (await cadastro.json()).accessToken as string;

  const pedido = await post('/service-requests', {
    categoria: 'Elétrica', descricao: 'Chuveiro queimado, preciso trocar a resistência.',
    lat: -3.7319, lng: -38.5267,
  }, { token: tokenCliente, headers: { 'X-Idempotency-Key': `aceito-${sufixo}` } });
  expect(pedido.status, 'criação do pedido pela API').toBe(201);
  const requestId = (await pedido.json()).id as string;

  const loginPrestador = await post('/auth/login', prestador);
  expect(loginPrestador.ok, `login do prestador falhou (HTTP ${loginPrestador.status})`).toBeTruthy();
  const tokenPrestador = (await loginPrestador.json()).accessToken as string;

  const proposta = await post(`/service-requests/${requestId}/proposals`, {
    valor: 180, prazoDias: 1, horarioProposto: new Date(Date.now() + 2 * 24 * 3600 * 1000).toISOString(),
  }, { token: tokenPrestador });
  expect(proposta.status, 'proposta do prestador (precisa estar aprovado)').toBe(201);
  const proposalId = (await proposta.json()).id as string;

  const aceite = await fetch(`${API}/proposals/${proposalId}/accept`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${tokenCliente}` },
  });
  expect(aceite.ok, `aceite da proposta falhou (HTTP ${aceite.status})`).toBeTruthy();
}
