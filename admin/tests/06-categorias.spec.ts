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
