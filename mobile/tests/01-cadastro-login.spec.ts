import { test, expect } from '@playwright/test';
import { registerCliente, registerPrestador, login } from './helpers/auth';

const ts = Date.now();

test.describe('Cadastro', () => {
  test('cadastro de cliente completo leva à Home', async ({ page }) => {
    await registerCliente(page, {
      nome: 'Lucia Teste',
      email: `lucia-cadastro-${ts}@onda.dev`,
      senha: 'senha1234',
    });
    // Home mostra os chips de categoria — sinal de que a sessão logou de verdade.
    await expect(page.getByText('Elétrica').first()).toBeVisible({ timeout: 8000 });
  });

  test('cadastro de prestador cai em EM VERIFICAÇÃO e depois de logar acessa Pedidos disponíveis', async ({ page }) => {
    await registerPrestador(page, {
      nome: 'Jose Prestador',
      cpf: '111.444.777-35',
      email: `jose-cadastro-${ts}@onda.dev`,
      senha: 'senha1234',
      categoria: 'Elétrica',
    });
    // registerPrestador já sai da tela EM VERIFICAÇÃO e loga de novo.
    await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  });
});

test.describe('Login', () => {
  const email = `lucia-login-${ts}@onda.dev`;
  const senha = 'senha1234';

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    await registerCliente(page, { nome: 'Lucia Login', email, senha });
    await page.close();
  });

  test('credenciais inválidas mostram erro e não navega', async ({ page }) => {
    await login(page, email, 'senha-errada');
    await expect(page.getByText(/Credenciais inválidas/i)).toBeVisible({ timeout: 8000 });
  });

  test('login válido leva à Home', async ({ page }) => {
    await login(page, email, senha);
    await expect(page.getByText('Elétrica').first()).toBeVisible({ timeout: 8000 });
  });
});
