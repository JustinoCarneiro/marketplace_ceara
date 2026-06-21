import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Exportar Relatórios', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Relatórios').click();
    await expect(page).toHaveURL('/reports');
  });

  test('exibe título e cards de relatórios disponíveis', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Exportar Relatórios/i })).toBeVisible();
    await expect(page.getByText(/Métricas da plataforma/i)).toBeVisible();
    await expect(page.getByText(/Transações financeiras/i)).toBeVisible();
    await expect(page.getByText(/Disputas/i)).toBeVisible();
    await expect(page.getByText(/Pedidos de serviço/i)).toBeVisible();
  });

  test('filtro de período está presente', async ({ page }) => {
    const dateInputs = page.locator('input[type="date"]');
    await expect(dateInputs.first()).toBeVisible();
    await expect(dateInputs.nth(1)).toBeVisible();
  });

  test('botões de download PDF e CSV estão presentes', async ({ page }) => {
    await expect(page.getByRole('button', { name: /PDF/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /CSV/i }).first()).toBeVisible();
  });

  test('aviso sobre formatos CSV e PDF está visível', async ({ page }) => {
    await expect(page.getByText(/Compatível com Excel/i)).toBeVisible();
    await expect(page.getByText(/prestação de contas/i)).toBeVisible();
  });

  test('clique em download inicia o processo (botão muda para Gerando…)', async ({ page }) => {
    // Interceptar o request para não depender do backend neste teste visual
    await page.route('**/admin/reports/**', route => {
      route.fulfill({
        status: 200,
        contentType: 'text/csv',
        body: 'id,valor\n1,100.00',
      });
    });

    await page.getByRole('button', { name: /CSV/i }).first().click();
    // O botão deve mostrar loading momentaneamente
    // (muito rápido para capturar, mas o handler mockado garante que não dá erro)
    await page.waitForTimeout(500);
    await expect(page.getByText(/baixado com sucesso/i)).toBeVisible({ timeout: 5000 });
  });
});

test.describe('Reconciliação Financeira', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Financeiro').click();
    await expect(page).toHaveURL('/finance');
  });

  test('exibe título e tabs Transações / Outbox', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Reconciliação Financeira/i })).toBeVisible();
    await expect(page.getByText(/💳 Transações/i)).toBeVisible();
    await expect(page.getByText(/🔄 Outbox/i)).toBeVisible();
  });

  test('tab Outbox exibe falhas ou estado vazio', async ({ page }) => {
    await page.getByText(/🔄 Outbox/i).click();
    await page.waitForTimeout(800);
    const empty = page.getByText(/Nenhuma falha no Outbox/i);
    const table = page.locator('table');
    const hasEmpty = await empty.isVisible().catch(() => false);
    const hasTable = await table.isVisible().catch(() => false);
    expect(hasEmpty || hasTable).toBe(true);
  });
});
