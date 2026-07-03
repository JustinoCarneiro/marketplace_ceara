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
});
