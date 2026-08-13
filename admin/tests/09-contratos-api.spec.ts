import { test, expect, request } from '@playwright/test';

/**
 * Testes de CONTRATO: comparam os campos que o backend realmente devolve com os campos que
 * as telas (admin e mobile) declaram consumir.
 *
 * Por que existem: `fetch().json()` é `any` nos dois frontends, então divergência de nome de
 * campo NÃO gera erro de compilação nem falha de teste de tela — só um valor `undefined` em
 * produção. Essa classe de bug sobreviveu a três rodadas de revisão de código e só apareceu
 * cruzando os dois lados. Exemplos reais já corrigidos que estes testes travam:
 *   - nearby devolvia `distanciaMetros`/`id`, mobile lia `distanciaKm`/`userId` → tela caía
 *     com TypeError em qualquer busca com resultado.
 *   - outbox devolvia `tipoEvento`/`agregado`, admin lia `tipo`/`entidade` → fila de falhas
 *     renderizava linhas em branco.
 *   - CategoryDto nunca teve `totalPrestadores`, a tela exibia "0 prestadores" como se fosse real.
 *
 * Se alguém renomear um campo no DTO Java sem atualizar o front, isto falha aqui.
 */

const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function tokenAdmin() {
  const ctx = await request.newContext();
  const res = await ctx.post(`${API}/auth/login`, {
    data: { email: 'admin@onda.com', senha: 'admin123' },
  });
  expect(res.ok(), 'login admin falhou — backend no ar com profile seed?').toBeTruthy();
  return (await res.json()).accessToken as string;
}

/** Falha citando exatamente o campo que sumiu, para o erro apontar a causa. */
function esperaCampos(objeto: Record<string, unknown>, campos: string[], contexto: string) {
  const presentes = Object.keys(objeto);
  const faltando = campos.filter(c => !presentes.includes(c));
  expect(
    faltando,
    `${contexto}: campos ausentes na resposta real ${JSON.stringify(faltando)}. ` +
    `Recebidos: ${JSON.stringify(presentes)}. Atualize o DTO do backend ou a interface do front.`,
  ).toEqual([]);
}

test.describe('Contrato backend ↔ frontends', () => {
  test('GET /providers/nearby entrega os campos que Home e Resultados leem', async () => {
    const ctx = await request.newContext();
    const login = await ctx.post(`${API}/auth/login`, {
      data: { email: 'maria@teste.com', senha: 'Senha@123' },
    });
    expect(login.ok()).toBeTruthy();
    const token = (await login.json()).accessToken;

    const res = await ctx.get(`${API}/providers/nearby?lat=-3.73&lng=-38.52&raio=50000`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.ok()).toBeTruthy();
    const lista = await res.json();
    test.skip(lista.length === 0, 'sem prestador com localização no seed — nada a comparar');

    // mobile/src/api/nearby.ts → NearbyProvider
    esperaCampos(lista[0],
      ['id', 'nome', 'categoria', 'bio', 'statusVerificacao', 'notaMedia', 'distanciaMetros'],
      'NearbyProviderDto vs mobile/src/api/nearby.ts');
  });

  test('GET /admin/outbox entrega os campos que a tela Financeiro lê', async () => {
    const ctx = await request.newContext();
    const res = await ctx.get(`${API}/admin/outbox?status=FALHA`, {
      headers: { Authorization: `Bearer ${await tokenAdmin()}` },
    });
    expect(res.ok()).toBeTruthy();
    const lista = await res.json();
    test.skip(lista.length === 0, 'sem evento em FALHA no seed — nada a comparar');

    // admin/src/pages/FinancePage.tsx → interface OutboxEvent
    esperaCampos(lista[0], ['id', 'agregado', 'tipoEvento', 'tentativas', 'status'],
      'OutboxAdminDto vs admin/src/pages/FinancePage.tsx');
  });

  test('GET /admin/transactions entrega os campos que a tela Financeiro lê', async () => {
    const ctx = await request.newContext();
    // Varre os 3 status (os mesmos que a tela concatena): fixar em RETIDO fazia este teste
    // pular sempre, porque 03-disputas resolve a disputa semeada antes daqui e a transação
    // já saiu de RETIDO para LIBERADO. Teste que pula não protege contrato nenhum.
    let lista: Record<string, unknown>[] = [];
    for (const status of ['RETIDO', 'LIBERADO', 'REEMBOLSADO']) {
      const res = await ctx.get(`${API}/admin/transactions?status=${status}`, {
        headers: { Authorization: `Bearer ${await tokenAdmin()}` },
      });
      expect(res.ok()).toBeTruthy();
      lista = await res.json();
      if (lista.length > 0) break;
    }
    test.skip(lista.length === 0, 'nenhuma transação em nenhum status — nada a comparar');

    // admin/src/pages/FinancePage.tsx → interface Transaction
    esperaCampos(lista[0], ['id', 'serviceRequestId', 'valorTotal', 'statusPagamento'],
      'TransacaoAdminDto vs admin/src/pages/FinancePage.tsx');
  });

  test('GET /admin/categories entrega os campos que a tela Categorias lê', async () => {
    const ctx = await request.newContext();
    const res = await ctx.get(`${API}/admin/categories`, {
      headers: { Authorization: `Bearer ${await tokenAdmin()}` },
    });
    expect(res.ok()).toBeTruthy();
    const lista = await res.json();
    test.skip(lista.length === 0, 'catálogo vazio no seed — nada a comparar');

    // admin/src/pages/CategoriesPage.tsx → interface Category
    esperaCampos(lista[0], ['id', 'nome', 'slug', 'ativa'],
      'CategoryDto vs admin/src/pages/CategoriesPage.tsx');
    // A tela exibia "{totalPrestadores ?? 0} prestadores" — campo que nunca existiu.
    expect(
      Object.keys(lista[0]),
      'CategoryDto ganhou totalPrestadores: a tela pode voltar a exibir a contagem real',
    ).not.toContain('totalPrestadores');
  });

  test('GET /admin/alerts entrega os campos que os chips do Dashboard leem', async () => {
    const ctx = await request.newContext();

    // Cria a própria condição em vez de depender do seed: quando este teste roda, 03-disputas
    // já resolveu a disputa semeada e 04-prestadores já reprovou o prestador semeado, então os
    // três contadores (SOS/DISPUTA/VERIFICACAO) estão zerados e o teste pulava sempre. Um
    // prestador novo entra em EM_VERIFICACAO e garante o alerta VERIFICACAO_INCONCLUSIVA.
    const registro = await ctx.post(`${API}/auth/register/provider`, {
      data: {
        nome: 'Contrato Alertas',
        email: `contrato-alertas-${Date.now()}@onda.dev`,
        senha: 'Senha@123',
        cpf: '111.444.777-35',
        categoria: 'Elétrica',
        aceitouTermos: true,
      },
    });
    expect(registro.ok(), 'não consegui criar prestador para gerar o alerta').toBeTruthy();

    const res = await ctx.get(`${API}/admin/alerts`, {
      headers: { Authorization: `Bearer ${await tokenAdmin()}` },
    });
    expect(res.ok()).toBeTruthy();
    const lista = await res.json();
    expect(lista.length, 'prestador recém-criado deveria gerar VERIFICACAO_INCONCLUSIVA')
      .toBeGreaterThan(0);

    // admin/src/pages/DashboardPage.tsx → interface OperationalAlert
    esperaCampos(lista[0], ['tipo', 'quantidade'],
      'OperationalAlert vs admin/src/pages/DashboardPage.tsx');
  });

  test('GET /admin/notifications entrega os campos que a Central e a Sidebar leem', async () => {
    const ctx = await request.newContext();
    const res = await ctx.get(`${API}/admin/notifications`, {
      headers: { Authorization: `Bearer ${await tokenAdmin()}` },
    });
    expect(res.ok()).toBeTruthy();
    const lista = await res.json();
    test.skip(lista.length === 0, 'sem notificação no seed — nada a comparar');

    // NotificationsPage (id/tipo/refId/criadoEm/lida) + Sidebar (lida, para o badge real)
    esperaCampos(lista[0], ['id', 'tipo', 'refId', 'criadoEm', 'lida'],
      'AdminNotificationDto vs NotificationsPage/Sidebar');
  });

  test('GET /admin/audit-logs entrega os campos que a Auditoria lê (inclui detalhe)', async () => {
    const ctx = await request.newContext();
    const res = await ctx.get(`${API}/admin/audit-logs`, {
      headers: { Authorization: `Bearer ${await tokenAdmin()}` },
    });
    expect(res.ok()).toBeTruthy();
    const lista = await res.json();
    test.skip(lista.length === 0, 'trilha vazia no seed — nada a comparar');

    // admin/src/pages/AuditPage.tsx → interface AuditLog (detalhe era gravado e nunca exposto)
    esperaCampos(lista[0], ['id', 'adminNome', 'acao', 'entidade', 'criadoEm', 'detalhe'],
      'AdminAuditLogDto vs admin/src/pages/AuditPage.tsx');
  });
});

test.describe('Idempotência é por dono (V14)', () => {
  test('mesma chave de clientes diferentes não devolve o pedido alheio', async () => {
    const ctx = await request.newContext();
    const chave = `contrato-${Date.now()}`;

    async function novoCliente(email: string) {
      const r = await ctx.post(`${API}/auth/register/client`, {
        data: { nome: 'Contrato', email, senha: 'Senha@123', aceitouTermos: true },
      });
      expect(r.ok()).toBeTruthy();
      return (await r.json()).accessToken as string;
    }

    const ts = Date.now();
    const tokenA = await novoCliente(`contrato-a-${ts}@teste.com`);
    const tokenB = await novoCliente(`contrato-b-${ts}@teste.com`);

    const criar = (token: string, descricao: string) => ctx.post(`${API}/service-requests`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Idempotency-Key': chave },
      data: { categoria: 'Elétrica', descricao, lat: -3.73, lng: -38.52 },
    });

    const pedidoA = await (await criar(tokenA, 'descricao do cliente A')).json();
    const pedidoB = await (await criar(tokenB, 'descricao do cliente B')).json();

    expect(pedidoB.id, 'B recebeu o pedido de A — vazamento por colisão de chave').not.toBe(pedidoA.id);
    expect(pedidoB.descricao).toBe('descricao do cliente B');

    // E a idempotência legítima continua valendo para o próprio dono.
    const repetidoA = await (await criar(tokenA, 'tentativa duplicada')).json();
    expect(repetidoA.id, 'mesma chave do MESMO cliente deveria devolver o mesmo pedido').toBe(pedidoA.id);
  });

  test('prestador não cria pedido como se fosse cliente', async () => {
    const ctx = await request.newContext();
    const ts = Date.now();
    const reg = await ctx.post(`${API}/auth/register/provider`, {
      data: {
        nome: 'Prestador Contrato', email: `contrato-prest-${ts}@teste.com`,
        senha: 'Senha@123', cpf: '111.444.777-35', categoria: 'Elétrica', aceitouTermos: true,
      },
    });
    expect(reg.ok()).toBeTruthy();
    const token = (await reg.json()).accessToken;

    const res = await ctx.post(`${API}/service-requests`, {
      headers: { Authorization: `Bearer ${token}`, 'X-Idempotency-Key': `prest-${ts}` },
      data: { categoria: 'Elétrica', descricao: 'prestador criando pedido', lat: -3.73, lng: -38.52 },
    });
    expect(res.status(), 'ROLE_PROVIDER não deve conseguir originar pedido de cliente').toBe(403);
  });
});
