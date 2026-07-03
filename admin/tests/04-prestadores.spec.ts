import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Moderação de Prestadores', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/providers');
    await expect(page).toHaveURL('/providers');
  });

  test('exibe título da página', async ({ page }) => {
    await expect(page.getByText(/Moderação de prestadores/i).first()).toBeVisible();
  });

  test('exibe os chips de filtro (Inconclusivos / Todos)', async ({ page }) => {
    await expect(page.getByText(/Inconclusivos/i)).toBeVisible();
    await expect(page.getByText('Todos')).toBeVisible();
  });

  test('estado vazio exibe mensagem', async ({ page }) => {
    // Ambiente de teste sobe sem prestadores cadastrados.
    await expect(page.getByText(/Nenhum prestador encontrado/i)).toBeVisible();
  });
});
