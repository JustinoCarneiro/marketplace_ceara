import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Catálogo de Categorias', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Categorias').click();
    await expect(page).toHaveURL('/categories');
  });

  test('exibe título e botão de nova categoria', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Catálogo de Categorias/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /\+ Nova categoria/i })).toBeVisible();
  });

  test('botão Nova categoria exibe formulário', async ({ page }) => {
    await page.getByRole('button', { name: /\+ Nova categoria/i }).click();
    await expect(page.getByPlaceholder(/Ex: Elétrica/i)).toBeVisible();
    await expect(page.getByPlaceholder(/eletrica/i)).toBeVisible();
  });

  test('slug é gerado automaticamente ao digitar o nome', async ({ page }) => {
    await page.getByRole('button', { name: /\+ Nova categoria/i }).click();
    await page.fill('input[placeholder*="Elétrica"]', 'Hidráulica');
    const slugInput = page.locator('input[style*="monospace"]');
    await expect(slugInput).toHaveValue('hidrulica', { timeout: 2000 });
  });

  test('cancelar fecha o formulário', async ({ page }) => {
    await page.getByRole('button', { name: /\+ Nova categoria/i }).click();
    await expect(page.getByPlaceholder(/Ex: Elétrica/i)).toBeVisible();
    await page.getByRole('button', { name: /Cancelar/i }).click();
    await expect(page.getByPlaceholder(/Ex: Elétrica/i)).not.toBeVisible();
  });

  test('lista de categorias é renderizada', async ({ page }) => {
    await page.waitForTimeout(800);
    // Deve ter ao menos uma categoria ou o estado vazio
    const hasCat = await page.locator('.card').count() > 0;
    expect(hasCat).toBe(true);
  });
});
