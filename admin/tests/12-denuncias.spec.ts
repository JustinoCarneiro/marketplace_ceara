import { test, expect, request } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

// Fila de denúncias (DenunciasPage): até aqui nenhum teste do painel abria a tela — só o backend
// (DenunciaServiceTest) e o fluxo mobile cobriam o canal. A tela carrega pelo hook useLista,
// mostra a idade pelo helper idadeDe e, ao resolver, tira a linha da lista local.
// Serial: o 2º teste resolve a denúncia que o 1º criou.
test.describe.configure({ mode: 'serial' });

const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';
const MOTIVO = `Golpe ${Date.now()}`;

async function login(ctx: Awaited<ReturnType<typeof request.newContext>>, email: string, senha: string) {
  const res = await ctx.post(`${API}/auth/login`, { data: { email, senha } });
  expect(res.ok(), `login de ${email} falhou — backend no ar com profile seed?`).toBeTruthy();
  return (await res.json()).accessToken as string;
}

test('uma denúncia feita pelo cliente aparece na fila com a idade', async ({ page }) => {
  const ctx = await request.newContext();
  const adminToken = await login(ctx, 'admin@onda.com', 'admin123');
  const usuarios = await (await ctx.get(`${API}/admin/users`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  })).json() as { id: string; role: string }[];
  const prestador = usuarios.find(u => u.role === 'ROLE_PROVIDER');
  expect(prestador, 'o seed tem de ter ao menos um prestador').toBeTruthy();

  const clienteToken = await login(ctx, 'maria@teste.com', 'Senha@123');
  const criada = await ctx.post(`${API}/denuncias`, {
    headers: { Authorization: `Bearer ${clienteToken}` },
    data: { tipo: 'PRESTADOR', alvoId: prestador!.id, motivo: MOTIVO, detalhes: 'denúncia criada pelo teste' },
  });
  expect(criada.status()).toBe(201);

  await loginAdmin(page);
  await page.goto('/denuncias');
  const linha = page.getByText(MOTIVO).locator('xpath=ancestor::div[.//button][1]');
  await expect(linha).toBeVisible();
  await expect(linha.getByText('Maria Teste')).toBeVisible();
  // Recém-criada: idade em horas ("0h"), nunca "NaNh" nem "undefined".
  await expect(linha.getByText(/^\d+h$/)).toBeVisible();
});

test('resolver tira a denúncia da fila e a baixa persiste no backend', async ({ page }) => {
  await loginAdmin(page);
  await page.goto('/denuncias');
  const linha = page.getByText(MOTIVO).locator('xpath=ancestor::div[.//button][1]');
  await linha.getByRole('button', { name: 'Resolver' }).click();
  await expect(page.getByText(MOTIVO)).toHaveCount(0);

  // Recarregar a página lê do backend de novo: a linha não volta.
  await page.reload();
  await expect(page.getByText('Fila de denúncias')).toBeVisible();
  await expect(page.getByText(MOTIVO)).toHaveCount(0);
});
