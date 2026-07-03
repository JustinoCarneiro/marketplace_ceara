import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Fila de Disputas', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/disputes');
    await expect(page).toHaveURL('/disputes');
  });

  test('exibe título da página', async ({ page }) => {
    await expect(page.getByText(/Fila de disputas/i).first()).toBeVisible();
  });

  test('exibe botão de exportar', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Exportar/i })).toBeVisible();
  });

  test('estado vazio exibe mensagem amigável', async ({ page }) => {
    // Ambiente de teste sobe sem disputas — a fila (status ABERTA) fica vazia.
    await expect(page.getByText(/Nenhuma disputa/i)).toBeVisible();
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
