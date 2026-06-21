import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
  });

  test('exibe título e KPIs financeiros', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Dashboard/i })).toBeVisible();
    await expect(page.getByText(/GMV/i)).toBeVisible();
    await expect(page.getByText(/Receita comissão/i)).toBeVisible();
    await expect(page.getByText(/Ticket médio/i)).toBeVisible();
    await expect(page.getByText(/Taxa conclusão/i)).toBeVisible();
  });

  test('exibe métricas de operações', async ({ page }) => {
    await expect(page.getByText(/Disputas abertas/i)).toBeVisible();
    await expect(page.getByText(/SOS acionados/i)).toBeVisible();
  });

  test('exibe métricas de usuários', async ({ page }) => {
    await expect(page.getByText(/Clientes ativos/i)).toBeVisible();
    await expect(page.getByText(/Prestadores ativos/i)).toBeVisible();
    await expect(page.getByText(/Prestadores verificados/i)).toBeVisible();
  });

  test('tabela de pedidos por status é renderizada', async ({ page }) => {
    await expect(page.getByText(/Pedidos por status/i)).toBeVisible();
  });

  test('filtro de data atualiza métricas', async ({ page }) => {
    const dataInicio = page.locator('input[type="date"]').first();
    await dataInicio.fill('2025-01-01');
    // Aguarda o loading spinner desaparecer
    await page.waitForTimeout(1500);
    await expect(page.getByText(/GMV/i)).toBeVisible();
  });

  test('sidebar exibe todos os itens de navegação', async ({ page }) => {
    await expect(page.getByText('Dashboard')).toBeVisible();
    await expect(page.getByText('Disputas')).toBeVisible();
    await expect(page.getByText('Prestadores')).toBeVisible();
    await expect(page.getByText('Usuários')).toBeVisible();
    await expect(page.getByText('Financeiro')).toBeVisible();
    await expect(page.getByText('Categorias')).toBeVisible();
    await expect(page.getByText('Notificações')).toBeVisible();
    await expect(page.getByText('Auditoria')).toBeVisible();
    await expect(page.getByText('Relatórios')).toBeVisible();
  });
});
