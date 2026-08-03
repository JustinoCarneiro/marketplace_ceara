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

  test('lista a disputa semeada com categoria e valor retido', async ({ page }) => {
    // SeedRunner cria 1 service_request EM_DISPUTA (categoria Hidráulica, R$ 250,00 retido).
    await expect(page.getByText('Hidráulica')).toBeVisible();
    // A soma "valor total retido" e a coluna "Valor retido" da linha mostram o mesmo
    // valor — precisa .first() ou o matcher bate em 2 elementos (strict mode).
    await expect(page.getByText(/R\$\s*250,00/).first()).toBeVisible();
    await expect(page.getByText('EM DISPUTA').first()).toBeVisible();
  });
});

test.describe('Mediação de Disputa (ação real)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/disputes');
  });

  test('abrir disputa mostra o detalhe real e resolver libera o valor ao prestador', async ({ page }) => {
    await page.getByText('Abrir →').first().click();
    await expect(page).toHaveURL(/\/disputes\/[0-9a-f-]+/);

    // Detalhe carregado a partir do DTO real (DisputaDetalheDto) — sem crash.
    await expect(page.getByText('Hidráulica')).toBeVisible();
    await expect(page.getByText(/R\$\s*250,00/)).toBeVisible();

    await page.getByRole('button', { name: /Liberar ao prestador/i }).click();
    await expect(page.getByText(/Disputa resolvida/i)).toBeVisible({ timeout: 8000 });

    // Confirma efeito real: a disputa resolvida (CONCLUIDO) sai da fila EM_DISPUTA.
    await page.getByRole('button', { name: /Voltar/i }).click();
    await expect(page).toHaveURL('/disputes');
    await expect(page.getByText(/Nenhuma disputa aberta/i)).toBeVisible();
  });
});
