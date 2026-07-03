import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Gestão de Usuários', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/users');
    await expect(page).toHaveURL('/users');
  });

  test('exibe título e campo de busca ao vivo', async ({ page }) => {
    await expect(page.getByText(/Gestão de usuários/i).first()).toBeVisible();
    await expect(page.getByPlaceholder(/Buscar por nome/i)).toBeVisible();
  });

  test('lista os usuários seedados', async ({ page }) => {
    // Ambiente de teste seeda admin@onda.com e maria@teste.com.
    await expect(page.getByText('admin@onda.com')).toBeVisible();
    await expect(page.getByText('maria@teste.com')).toBeVisible();
  });

  test('busca por nome filtra a lista ao vivo', async ({ page }) => {
    await page.getByPlaceholder(/Buscar por nome/i).fill('maria');
    await expect(page.getByText('maria@teste.com')).toBeVisible();
    await expect(page.getByText('admin@onda.com')).not.toBeVisible();
  });
});
