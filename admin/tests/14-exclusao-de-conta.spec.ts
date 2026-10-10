import { test, expect, request } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

/**
 * US36 — a conta excluída no painel admin. A conta é criada e excluída DE VERDADE pela API
 * (cadastro → POST /users/me/delete), então o que a tela mostra é o que o backend devolve.
 *
 * Antes de a tela tratar EXCLUIDO, qualquer status diferente de SUSPENSO aparecia como "ATIVO" com o
 * botão "Suspender": o admin via uma conta já apagada como se estivesse funcionando, e o clique só
 * devolvia erro.
 */

const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function contaExcluida() {
  const ctx = await request.newContext();
  const email = `excluida.${Date.now()}@teste.com`;
  const reg = await ctx.post(`${API}/auth/register/client`, {
    data: { nome: 'Cliente Que Saiu', email, senha: 'Senha@123', aceitouTermos: true },
  });
  expect(reg.status(), 'cadastro da conta de teste').toBe(201);
  const { accessToken, userId } = await reg.json();

  const exclusao = await ctx.post(`${API}/users/me/delete`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    data: { senha: 'Senha@123' },
  });
  expect(exclusao.status(), 'exclusão da conta de teste').toBe(204);
  return { userId: userId as string };
}

async function tokenAdmin() {
  const ctx = await request.newContext();
  const res = await ctx.post(`${API}/auth/login`, { data: { email: 'admin@onda.com', senha: 'admin123' } });
  expect(res.ok(), 'login admin falhou — backend no ar com profile seed?').toBeTruthy();
  return (await res.json()).accessToken as string;
}

test.describe('Conta excluída no painel (US36)', () => {
  test('aparece como "Usuário removido", EXCLUÍDO, sem ação de suspender ou reativar', async ({ page }) => {
    const { userId } = await contaExcluida();

    await loginAdmin(page);
    await page.goto('/users');

    const linha = page.getByTestId(`usuario-${userId}`);
    await expect(linha.getByText('Usuário removido')).toBeVisible();
    await expect(linha.getByText('EXCLUÍDO', { exact: true })).toBeVisible();
    // não pode parecer uma conta viva...
    await expect(linha.getByText('ATIVO', { exact: true })).toHaveCount(0);
    await expect(linha.getByText('SUSPENSO', { exact: true })).toHaveCount(0);
    // ...nem oferecer ação que o backend recusa
    await expect(linha.getByRole('button')).toHaveCount(0);
  });

  test('o backend recusa suspender e reativar a conta excluída (422 ACCOUNT_DELETED, não 500)', async () => {
    const { userId } = await contaExcluida();
    const ctx = await request.newContext();
    const headers = { Authorization: `Bearer ${await tokenAdmin()}` };

    for (const acao of ['suspend', 'reactivate']) {
      const res = await ctx.post(`${API}/admin/users/${userId}/${acao}`, { headers, data: {} });
      expect(res.status(), `${acao} de conta excluída`).toBe(422);
      expect((await res.json()).code).toBe('ACCOUNT_DELETED');
    }
  });
});
