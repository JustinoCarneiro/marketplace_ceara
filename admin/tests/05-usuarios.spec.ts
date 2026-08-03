import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Gestão de Usuários', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/users');
    await expect(page).toHaveURL('/users');
  });

  test('exibe título e campo de busca ao vivo', async ({ page }) => {
    await expect(page.getByText(/Gestão de usuários/i).first()).toBeVisible();
    await expect(page.getByPlaceholder(/Buscar por nome/i)).toBeVisible();
  });

  test('lista os usuários seedados', async ({ page }) => {
    // Ambiente de teste seeda admin@onda.com e maria@teste.com.
    await expect(page.getByText('admin@onda.com')).toBeVisible();
    await expect(page.getByText('maria@teste.com')).toBeVisible();
  });

  test('busca por nome filtra a lista ao vivo', async ({ page }) => {
    await page.getByPlaceholder(/Buscar por nome/i).fill('maria');
    await expect(page.getByText('maria@teste.com')).toBeVisible();
    await expect(page.getByText('admin@onda.com')).not.toBeVisible();
  });
});

test.describe('Gestão de Usuários (ação real)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/users');
  });

  // A tabela não tem test-id por linha e admin@onda.com também mostra um botão
  // "Suspender" — um locator solto por texto pega os dois. `ancestor::div[.//button][1]`
  // sobe do e-mail até a div-linha mais próxima que já contém o botão, sem ambiguidade.
  function rowFor(page: import('@playwright/test').Page, email: string) {
    return page.getByText(email, { exact: true }).locator('xpath=ancestor::div[.//button][1]');
  }

  test('suspender e reativar maria persiste o status a cada passo', async ({ page }) => {
    const row = rowFor(page, 'maria@teste.com');

    await row.getByRole('button', { name: /Suspender/i }).click();
    await expect(row.getByText('SUSPENSO')).toBeVisible({ timeout: 8000 });
    await expect(row.getByRole('button', { name: /Reativar/i })).toBeVisible();

    // Recarrega para confirmar que persistiu no backend, não só no estado local.
    await page.reload();
    const rowAfterReload = rowFor(page, 'maria@teste.com');
    await expect(rowAfterReload.getByText('SUSPENSO')).toBeVisible();

    // Reverte — deixa o ambiente como estava para os demais testes/reruns.
    await rowAfterReload.getByRole('button', { name: /Reativar/i }).click();
    await expect(rowAfterReload.getByText('ATIVO')).toBeVisible({ timeout: 8000 });
  });
});
