import { test, expect, Page } from '@playwright/test';
import { login } from './helpers/auth';

// Limite de tentativas de senha (US37): 5 erros seguidos bloqueiam a conta por 15 min (429), no login e na exclusão de
// conta, mesmo com a senha certa. O backend tem E2E real contra o Postgres; aqui se prova o que o USUÁRIO vê: a mensagem
// do servidor (quanto esperar) em vez de um "senha errada" que seria mentira, e que a tela não avança.
// Contas NOVAS por teste: o bloqueio dura 15 min e travaria qualquer outro teste que reaproveitasse a conta.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem isso o worker novo
// reavalia `ts` e o login procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();
const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';
const SENHA = 'senha1234';
const MENSAGEM_BLOQUEIO = /Muitas tentativas de senha\. Tente de novo em \d+ minutos?\./;

const LOGIN = { nome: 'Lia Login', email: `lia-login-${ts}@onda.dev` };
const EXCLUSAO = { nome: 'Edu Exclusao', email: `edu-exclusao-${ts}@onda.dev` };

async function cadastrarPelaApi(c: { nome: string; email: string }) {
  const res = await fetch(`${API}/auth/register/client`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...c, senha: SENHA, aceitouTermos: true }),
  });
  expect(res.status, 'cadastro pela API').toBe(201);
}

/** Clica e espera a resposta do login chegar: sem isso o teste enxergaria o erro da tentativa ANTERIOR. */
async function tentarLogin(page: Page, senha: string) {
  await page.getByPlaceholder('••••••••').fill(senha);
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/auth/login')),
    page.getByText('Entrar', { exact: true }).click(),
  ]);
}

test('setup: duas contas novas, pela API', async () => {
  await cadastrarPelaApi(LOGIN);
  await cadastrarPelaApi(EXCLUSAO);
});

test('login: 5 senhas erradas bloqueiam; a mensagem diz quanto esperar e nem a senha certa entra', async ({ page }) => {
  await login(page, LOGIN.email, 'errada-1');
  await expect(page.getByText('Credenciais inválidas.')).toBeVisible({ timeout: 8000 });
  for (let i = 2; i <= 5; i++) {
    await tentarLogin(page, `errada-${i}`);
    await expect(page.getByText('Credenciais inválidas.')).toBeVisible();
  }

  // a 6ª, com a senha CERTA: bloqueada, e a tela diz por quê (não "credenciais inválidas")
  await tentarLogin(page, SENHA);
  await expect(page.getByText(MENSAGEM_BLOQUEIO)).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Credenciais inválidas.')).toHaveCount(0);
  await expect(page.getByText('Criar pedido', { exact: true })).toHaveCount(0);
});

test('exclusão de conta: 5 senhas erradas bloqueiam; a certa também é recusada e a conta fica', async ({ page }) => {
  await login(page, EXCLUSAO.email, SENHA);
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Perfil', { exact: true }).click();
  await page.getByTestId('link-excluir-conta').click();
  await expect(page.getByTestId('input-senha-exclusao')).toBeVisible({ timeout: 8000 });

  for (let i = 1; i <= 5; i++) {
    await page.getByTestId('input-senha-exclusao').fill(`errada-${i}`);
    await Promise.all([
      page.waitForResponse(r => r.url().includes('/users/me/delete')),
      page.getByTestId('btn-excluir-conta').click(),
    ]);
    await expect(page.getByTestId('erro-exclusao')).toHaveText('Senha incorreta.');
  }

  await page.getByTestId('input-senha-exclusao').fill(SENHA);
  await Promise.all([
    page.waitForResponse(r => r.url().includes('/users/me/delete')),
    page.getByTestId('btn-excluir-conta').click(),
  ]);
  await expect(page.getByTestId('erro-exclusao')).toHaveText(MENSAGEM_BLOQUEIO);
  await expect(page.getByTestId('conta-excluida')).toHaveCount(0);
});
