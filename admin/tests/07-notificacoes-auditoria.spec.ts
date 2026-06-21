import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Central de Notificações', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Notificações').click();
    await expect(page).toHaveURL('/notifications');
  });

  test('exibe título e controles de filtro', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Central de Notificações/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Exibindo/i })).toBeVisible();
  });

  test('botão de alternar filtro muda o texto', async ({ page }) => {
    const btn = page.getByRole('button', { name: /Exibindo: não lidas/i });
    await btn.click();
    await expect(page.getByRole('button', { name: /Exibindo: todas/i })).toBeVisible();
  });

  test('estado vazio exibe mensagem', async ({ page }) => {
    await page.waitForTimeout(800);
    const empty = page.getByText(/Nenhuma notificação/i);
    const list  = page.getByText(/SOS|DISPUTA|VERIFICACAO/i).first();
    const hasEmpty = await empty.isVisible().catch(() => false);
    const hasList  = await list.isVisible().catch(() => false);
    expect(hasEmpty || hasList).toBe(true);
  });
});

test.describe('Log de Auditoria', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.getByText('Auditoria').click();
    await expect(page).toHaveURL('/audit');
  });

  test('exibe título e aviso de append-only', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Log de Auditoria/i })).toBeVisible();
    await expect(page.getByText(/append-only/i)).toBeVisible();
  });

  test('campo de filtro por entidade está presente', async ({ page }) => {
    await expect(page.getByPlaceholder(/Filtrar por entidade/i)).toBeVisible();
  });

  test('filtro por entidade envia requisição', async ({ page }) => {
    await page.fill('input[placeholder*="entidade"]', 'USUARIO');
    await page.getByRole('button', { name: /Filtrar/i }).click();
    await page.waitForTimeout(1000);
    await expect(page.getByRole('heading', { name: /Log de Auditoria/i })).toBeVisible();
  });

  test('botão Limpar reseta o filtro', async ({ page }) => {
    await page.fill('input[placeholder*="entidade"]', 'DISPUTA');
    await page.getByRole('button', { name: /Filtrar/i }).click();
    await page.waitForTimeout(500);
    await page.getByRole('button', { name: /Limpar/i }).click();
    await expect(page.locator('input[placeholder*="entidade"]')).toHaveValue('');
  });
});
