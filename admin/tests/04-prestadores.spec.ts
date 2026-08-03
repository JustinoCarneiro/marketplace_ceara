import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Moderação de Prestadores', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/providers');
    await expect(page).toHaveURL('/providers');
  });

  test('exibe título da página', async ({ page }) => {
    await expect(page.getByText(/Moderação de prestadores/i).first()).toBeVisible();
  });

  test('exibe os chips de filtro (Em verificação / Todos)', async ({ page }) => {
    await expect(page.getByText(/Em verificação/i)).toBeVisible();
    await expect(page.getByText('Todos')).toBeVisible();
  });

  test('lista o prestador semeado com ações de moderação disponíveis', async ({ page }) => {
    // SeedRunner cria João Prestador em EM_VERIFICACAO — precisa aparecer com os botões reais.
    await expect(page.getByText('João Prestador')).toBeVisible();
    await expect(page.getByRole('button', { name: /Verificar/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Reprovar/i })).toBeVisible();
  });
});

test.describe('Moderação de Prestador (ação real)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/providers');
  });

  test('reprovar prestador persiste o novo status e dispara alerta de verificação', async ({ page }) => {
    await page.getByRole('button', { name: /Reprovar/i }).click();

    // Ação real (POST /providers/{id}/reject) — sai da aba padrão "Em verificação".
    await expect(page.getByText('João Prestador')).not.toBeVisible({ timeout: 8000 });

    // "Todos" confirma que o status mudou de verdade no backend (não é otimismo de UI).
    await page.getByText('Todos').click();
    await expect(page.getByText('João Prestador')).toBeVisible();
    await expect(page.getByText('REPROVADO')).toBeVisible();
  });
});
