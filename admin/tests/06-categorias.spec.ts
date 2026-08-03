import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Catálogo de Categorias', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/categories');
    await expect(page).toHaveURL('/categories');
  });

  test('exibe título e botão de nova categoria', async ({ page }) => {
    await expect(page.getByText(/Catálogo de categorias/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Nova categoria/i })).toBeVisible();
  });

  test('botão Nova categoria abre o formulário', async ({ page }) => {
    await page.getByRole('button', { name: /Nova categoria/i }).click();
    await expect(page.getByPlaceholder(/Nome da categoria/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Criar/i })).toBeVisible();
  });

  test('botão Nova categoria alterna o formulário (abre e fecha)', async ({ page }) => {
    const btn = page.getByRole('button', { name: /Nova categoria/i });
    await btn.click();
    await expect(page.getByPlaceholder(/Nome da categoria/i)).toBeVisible();
    await btn.click();
    await expect(page.getByPlaceholder(/Nome da categoria/i)).not.toBeVisible();
  });
});

test.describe('Catálogo de Categorias (ação real)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/categories');
  });

  test('criar categoria persiste via POST e aparece na lista após reload', async ({ page }) => {
    const nome = `Categoria E2E ${Date.now()}`;

    await page.getByRole('button', { name: /Nova categoria/i }).click();
    await page.getByPlaceholder(/Nome da categoria/i).fill(nome);
    await page.getByRole('button', { name: /Criar/i }).click();

    // O form fecha (setShowForm(false)) só depois do POST resolver — sinal de sucesso real.
    await expect(page.getByPlaceholder(/Nome da categoria/i)).not.toBeVisible({ timeout: 8000 });
    await expect(page.getByText(nome)).toBeVisible();
    await expect(page.getByText('ATIVA').first()).toBeVisible();

    // Recarrega para confirmar que veio do banco (GET /admin/categories), não só do estado local.
    await page.reload();
    await expect(page.getByText(nome)).toBeVisible();
  });
});
