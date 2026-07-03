import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
  });

  test('exibe título e KPIs financeiros', async ({ page }) => {
    await expect(page.getByText(/Dashboard/i).first()).toBeVisible();
    await expect(page.getByText(/GMV/i)).toBeVisible();
    await expect(page.getByText(/Receita comissão/i)).toBeVisible();
    await expect(page.getByText(/Ticket médio/i)).toBeVisible();
    await expect(page.getByText(/Taxa de conclusão/i)).toBeVisible();
  });

  test('exibe métricas de operações', async ({ page }) => {
    await expect(page.getByText(/Disputas abertas/i)).toBeVisible();
    await expect(page.getByText(/SOS acionados/i)).toBeVisible();
  });

  test('exibe métricas de usuários', async ({ page }) => {
    await expect(page.getByText(/Clientes ativos/i)).toBeVisible();
    await expect(page.getByText(/Prestadores verificados/i)).toBeVisible();
  });

  test('tabela de pedidos por status é renderizada', async ({ page }) => {
    await expect(page.getByText(/Pedidos por status/i)).toBeVisible();
  });

  test('sidebar exibe todos os itens de navegação', async ({ page }) => {
    // Itens de navegação são links (NavLink) — usa role p/ não colidir com textos da página
    for (const label of [
      /Dashboard/, /Disputas/, /Moderação/, /Usuários/, /Financeiro/,
      /Categorias/, /Auditoria/, /Notificações/, /Relatórios/,
    ]) {
      await expect(page.getByRole('link', { name: label }).first()).toBeVisible();
    }
  });
});
