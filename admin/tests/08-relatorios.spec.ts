import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Exportar Relatórios', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/reports');
    await expect(page).toHaveURL('/reports');
  });

  test('exibe título e opções de formato CSV/PDF', async ({ page }) => {
    await expect(page.getByText(/Exportar relatório/i).first()).toBeVisible();
    await expect(page.getByText('CSV')).toBeVisible();
    await expect(page.getByText('PDF')).toBeVisible();
  });

  test('exibe botão Gerar relatório', async ({ page }) => {
    await expect(page.getByRole('button', { name: /Gerar relatório/i })).toBeVisible();
  });

  test('exibe aviso de LGPD (sem dados pessoais)', async ({ page }) => {
    await expect(page.getByText(/não incluem dados pessoais/i)).toBeVisible();
  });
});

test.describe('Exportar Relatórios (download real)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/reports');
  });

  test('gerar CSV baixa um arquivo de verdade (GET /admin/reports/requests.csv)', async ({ page }) => {
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /Gerar relatório/i }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('pedidos.csv');
    const path = await download.path();
    expect(path).toBeTruthy();
    await expect(page.getByText(/Relatório pronto/i)).toBeVisible();
  });

  test('gerar PDF baixa um arquivo de verdade (GET /admin/reports/metrics.pdf)', async ({ page }) => {
    await page.getByText('PDF', { exact: true }).click();
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /Gerar relatório/i }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('metrics.pdf');
    const path = await download.path();
    expect(path).toBeTruthy();
  });
});

test.describe('Reconciliação Financeira', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/finance');
    await expect(page).toHaveURL('/finance');
  });

  test('exibe título e KPIs de reconciliação', async ({ page }) => {
    await expect(page.getByText(/Reconciliação financeira/i).first()).toBeVisible();
    await expect(page.getByText(/Retido/i).first()).toBeVisible();
    await expect(page.getByText(/Reembolsado/i).first()).toBeVisible();
  });

  test('tabela de transações é renderizada', async ({ page }) => {
    await expect(page.getByText('Transação')).toBeVisible();
    await expect(page.getByText('Valor')).toBeVisible();
  });

  test('lista a transação liberada pela disputa resolvida (03-disputas)', async ({ page }) => {
    // GET /admin/transactions filtra por 1 status (default RETIDO) — a página buscava só
    // esse, então LIBERADO/REEMBOLSADO nunca apareciam mesmo existindo de verdade. Por essa
    // altura da suíte o OutboxProcessor (roda a cada 5s) já processou o PAYMENT_RELEASED
    // da disputa resolvida em 03-disputas.
    await expect(page.getByText('LIBERADO').first()).toBeVisible();
  });

  test('exportar baixa um CSV de transações de verdade', async ({ page }) => {
    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /Exportar/i }).click(),
    ]);
    expect(download.suggestedFilename()).toBe('transacoes.csv');
    expect(await download.path()).toBeTruthy();
  });
});
