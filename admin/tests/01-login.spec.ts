import { test, expect } from '@playwright/test';

/**
 * Testes de autenticação do painel admin.
 * Pré-requisito: backend rodando em localhost:8080 com usuário admin seedado.
 */

test.describe('Login Admin', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('exibe formulário de login com campos e-mail e senha', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Onda Admin/i })).toBeVisible();
    await expect(page.locator('input[type="email"]')).toBeVisible();
    await expect(page.locator('input[type="password"]')).toBeVisible();
    await expect(page.getByRole('button', { name: /Entrar/i })).toBeVisible();
  });

  test('exibe banner de auditoria na tela de login', async ({ page }) => {
    await expect(page.getByText(/log de auditoria/i)).toBeVisible();
  });

  test('credenciais inválidas exibem mensagem de erro', async ({ page }) => {
    await page.fill('input[type="email"]', 'errado@onda.com');
    await page.fill('input[type="password"]', 'senhaerrada');
    await page.getByRole('button', { name: /Entrar/i }).click();
    await expect(page.getByText(/credenciais inválidas/i)).toBeVisible({ timeout: 8000 });
  });

  test('usuário não-admin é rejeitado com mensagem clara', async ({ page }) => {
    // Usar conta de cliente que não tem ROLE_ADMIN
    await page.fill('input[type="email"]', 'maria@teste.com');
    await page.fill('input[type="password"]', 'Senha@123');
    await page.getByRole('button', { name: /Entrar/i }).click();
    await expect(page.getByText(/restrito a administradores/i)).toBeVisible({ timeout: 8000 });
  });

  test('login com admin válido redireciona para o Dashboard', async ({ page }) => {
    await page.fill('input[type="email"]', 'admin@onda.com');
    await page.fill('input[type="password"]', 'admin123');
    await page.getByRole('button', { name: /Entrar/i }).click();
    await expect(page).toHaveURL('/', { timeout: 10000 });
    await expect(page.getByRole('heading', { name: /Dashboard/i })).toBeVisible();
  });

  test('rota protegida redireciona para /login sem token', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login');
  });
});
