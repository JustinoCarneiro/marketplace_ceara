import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Central de Notificações', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/notifications');
    await expect(page).toHaveURL('/notifications');
  });

  test('exibe título e abas de filtro (Todas / Não lidas)', async ({ page }) => {
    await expect(page.getByText(/Central de notificações/i).first()).toBeVisible();
    await expect(page.getByText('Todas', { exact: true })).toBeVisible();
    await expect(page.getByText(/Não lidas/i)).toBeVisible();
  });

  test('estado vazio exibe mensagem', async ({ page }) => {
    // Ambiente de teste sobe sem alertas.
    await expect(page.getByText(/Nenhum alerta pendente/i)).toBeVisible();
  });
});

test.describe('Log de Auditoria', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/audit');
    await expect(page).toHaveURL('/audit');
  });

  test('exibe título e selo somente-leitura', async ({ page }) => {
    await expect(page.getByText(/Log de auditoria/i).first()).toBeVisible();
    await expect(page.getByText(/Somente leitura/i)).toBeVisible();
  });

  test('exibe cabeçalhos da tabela de trilha', async ({ page }) => {
    await expect(page.getByText('Entidade')).toBeVisible();
    await expect(page.getByText('Quando')).toBeVisible();
  });

  test('estado vazio exibe mensagem', async ({ page }) => {
    // Sem ações administrativas no ambiente de teste.
    await expect(page.getByText(/Nenhum registro encontrado/i)).toBeVisible();
  });
});
