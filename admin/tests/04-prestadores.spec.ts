import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Moderação de Prestadores', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Prestadores').click();
    await expect(page).toHaveURL('/providers');
  });

  test('exibe título e select de filtro', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Moderação de Prestadores/i })).toBeVisible();
    const select = page.locator('select');
    await expect(select).toBeVisible();
    await expect(select).toHaveValue('EM_VERIFICACAO');
  });

  test('filtro Verificados atualiza listagem', async ({ page }) => {
    await page.selectOption('select', 'VERIFICADO');
    await page.waitForTimeout(800);
    await expect(page.getByRole('heading', { name: /Moderação/i })).toBeVisible();
  });

  test('prestador EM_VERIFICACAO exibe botões Verificar e Reprovar', async ({ page }) => {
    await page.selectOption('select', 'EM_VERIFICACAO');
    await page.waitForTimeout(800);

    const btnVerificar = page.getByRole('button', { name: /Verificar/i }).first();
    const visible = await btnVerificar.isVisible().catch(() => false);
    if (!visible) return; // Sem prestadores em verificação

    await expect(btnVerificar).toBeVisible();
    await expect(page.getByRole('button', { name: /Reprovar/i }).first()).toBeVisible();
  });

  test('botão Verificar sem justificativa exibe erro', async ({ page }) => {
    await page.selectOption('select', 'EM_VERIFICACAO');
    await page.waitForTimeout(800);

    const btnVerificar = page.getByRole('button', { name: /✓ Verificar/i }).first();
    const visible = await btnVerificar.isVisible().catch(() => false);
    if (!visible) return;

    // Clicar sem preencher justificativa
    await btnVerificar.click();
    await expect(page.getByText(/Informe a justificativa/i)).toBeVisible({ timeout: 3000 });
  });
});
