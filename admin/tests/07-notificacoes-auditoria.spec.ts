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

  test('lista o alerta de verificação gerado ao reprovar o prestador semeado', async ({ page }) => {
    // O teste 04-prestadores reprova João Prestador, o que dispara
    // notificationService.criarAlerta("VERIFICACAO", ...) — deve chegar aqui.
    await expect(page.getByText(/Não lidas · [1-9]/)).toBeVisible();
    await expect(page.getByText('Verificação de prestador pendente')).toBeVisible();
  });
});

test.describe('Central de Notificações (ação real)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/notifications');
  });

  test('marcar todas como lidas zera o contador de não lidas', async ({ page }) => {
    await expect(page.getByText(/Não lidas · [1-9]/)).toBeVisible();
    await page.getByText('Marcar todas como lidas').click();
    // POST /admin/notifications/mark-all-read + reload da lista.
    await expect(page.getByText('Não lidas · 0')).toBeVisible({ timeout: 8000 });
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

  test('registra as ações administrativas reais executadas pela suíte', async ({ page }) => {
    // Por essa altura da suíte já rodaram: reprovar prestador (04), resolver disputa (03),
    // suspender/reativar usuário (05) e criar categoria (06) — todas auditadas no AdminController.
    await expect(page.getByText('REPROVAR_PRESTADOR')).toBeVisible();
    await expect(page.getByText('RESOLVER_DISPUTA')).toBeVisible();
    await expect(page.getByText('CRIAR_CATEGORIA')).toBeVisible();
    await expect(page.getByText('Nenhum registro encontrado')).not.toBeVisible();
  });
});
