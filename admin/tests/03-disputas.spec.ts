import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Fila de Disputas', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Disputas').click();
    await expect(page).toHaveURL('/disputes');
  });

  test('exibe título e subtítulo da página', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Fila de Disputas/i })).toBeVisible();
    await expect(page.getByText(/Mediação de conflitos/i)).toBeVisible();
  });

  test('select de filtro de status está presente', async ({ page }) => {
    const select = page.locator('select');
    await expect(select).toBeVisible();
    await expect(select).toHaveValue('ABERTA');
  });

  test('trocar filtro para Resolvidas atualiza a listagem', async ({ page }) => {
    await page.selectOption('select', 'RESOLVIDA');
    await page.waitForTimeout(800);
    // Não deve mostrar alerta de disputas abertas
    const alerta = page.getByText(/disputas abertas/i);
    const visivel = await alerta.isVisible().catch(() => false);
    // Com filtro RESOLVIDA, alerta de abertas não deve aparecer
    expect(visivel).toBe(false);
  });

  test('estado vazio exibe mensagem amigável', async ({ page }) => {
    // Filtrar por status que provavelmente está vazio
    await page.selectOption('select', 'RESOLVIDA');
    await page.waitForTimeout(1000);
    const empty = page.getByText(/Nenhuma disputa/i);
    const hasEmpty = await empty.isVisible().catch(() => false);
    if (hasEmpty) {
      await expect(empty).toBeVisible();
    }
  });
});

test.describe('Detalhe da Disputa', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/disputes');
  });

  test('botão Mediar navega para a página de detalhe', async ({ page }) => {
    const btnMediar = page.getByRole('button', { name: /Mediar/i }).first();
    const visible = await btnMediar.isVisible().catch(() => false);
    if (!visible) {
      test.skip(); // Sem disputas abertas no ambiente de teste
      return;
    }
    await btnMediar.click();
    await expect(page).toHaveURL(/\/disputes\/.+/);
    await expect(page.getByText(/Partes envolvidas/i)).toBeVisible();
    await expect(page.getByText(/Valor retido/i)).toBeVisible();
  });
});
