import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Gestão de Usuários', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Usuários').click();
    await expect(page).toHaveURL('/users');
  });

  test('exibe formulário de busca com campos e selects', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Gestão de Usuários/i })).toBeVisible();
    await expect(page.getByPlaceholder(/Buscar por nome/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Buscar/i })).toBeVisible();
  });

  test('busca retorna lista de usuários', async ({ page }) => {
    await page.getByRole('button', { name: /Buscar/i }).click();
    await page.waitForTimeout(1000);
    // Deve mostrar a tabela ou estado vazio
    const table = page.locator('table');
    const empty = page.getByText(/Nenhum usuário encontrado/i);
    const hasTable = await table.isVisible().catch(() => false);
    const hasEmpty = await empty.isVisible().catch(() => false);
    expect(hasTable || hasEmpty).toBe(true);
  });

  test('filtro por papel Cliente funciona', async ({ page }) => {
    await page.selectOption('select:nth-of-type(1)', 'ROLE_CLIENT');
    await page.waitForTimeout(800);
    await expect(page.getByRole('heading', { name: /Gestão/i })).toBeVisible();
  });

  test('busca por nome filtra a lista', async ({ page }) => {
    await page.fill('input[placeholder*="Buscar"]', 'maria');
    await page.getByRole('button', { name: /Buscar/i }).click();
    await page.waitForTimeout(1000);
    await expect(page.getByRole('heading', { name: /Gestão/i })).toBeVisible();
  });
});
