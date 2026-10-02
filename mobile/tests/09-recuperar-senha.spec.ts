import { test, expect, Page } from '@playwright/test';
import { registerCliente } from './helpers/auth';
import { lerCodigoRecebido, recebeuEmail } from './helpers/emails';

// US35 — "Esqueci a senha". Até aqui o link do login mostrava só um alerta "Em breve" (que, no
// react-native-web, nem aparece). Estes testes percorrem o fluxo de verdade, pela interface, contra
// o backend real: pedir o código, ler o e-mail, errar, acertar, entrar com a senha nova.
// Os limites de segurança (tentativas, expiração, sessões encerradas, a trava de escrita) rodam
// contra o Postgres no E2E do backend; aqui é a ponta da tela.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts).
test.describe.configure({ mode: 'serial' });
const ts = Date.now();

const CLIENTE = { nome: 'Rita Recupera', email: `rita-recupera-${ts}@onda.dev`, senha: 'senha1234' };
const NOVA_SENHA = 'novaSenha5678';

async function abrirRecuperacao(page: Page) {
  await page.goto('/');
  await page.getByText('Já tenho conta', { exact: true }).click();
  await page.getByTestId('link-esqueci-senha').click();
  await expect(page.getByTestId('input-email-recuperacao')).toBeVisible();
}

async function pedirCodigo(page: Page, email: string) {
  await page.getByTestId('input-email-recuperacao').fill(email);
  await page.getByTestId('btn-enviar-codigo').click();
  await expect(page.getByTestId('input-codigo-recuperacao')).toBeVisible({ timeout: 10000 });
}

test('setup: cadastra a cliente', async ({ page }) => {
  await registerCliente(page, CLIENTE);
});

test('"Esqueci a senha" abre a recuperação (não é mais um alerta "Em breve")', async ({ page }) => {
  await abrirRecuperacao(page);
  await expect(page.getByText('Informe o e-mail da sua conta')).toBeVisible();
});

test('e-mail vazio ou malformado é barrado na própria tela', async ({ page }) => {
  await abrirRecuperacao(page);

  await page.getByTestId('btn-enviar-codigo').click();
  await expect(page.getByTestId('erro-recuperacao')).toHaveText('Informe um e-mail válido.');

  await page.getByTestId('input-email-recuperacao').fill('isto-nao-e-um-email');
  await page.getByTestId('btn-enviar-codigo').click();
  await expect(page.getByTestId('erro-recuperacao')).toHaveText('Informe um e-mail válido.');
});

test('a tela de nova senha valida o que dá pra validar e mostra a mensagem do servidor para código errado', async ({ page }) => {
  await abrirRecuperacao(page);
  await pedirCodigo(page, CLIENTE.email);

  // código incompleto
  await page.getByTestId('btn-redefinir-senha').click();
  await expect(page.getByTestId('erro-recuperacao'))
    .toHaveText('Digite o código de 8 caracteres que enviamos por e-mail.');

  // o campo mostra o código como o e-mail: maiúsculas e hífen depois da 4ª posição
  await page.getByTestId('input-codigo-recuperacao').fill('zzzz9999');
  await expect(page.getByTestId('input-codigo-recuperacao')).toHaveValue('ZZZZ-9999');

  await page.getByTestId('input-nova-senha').fill('curta');
  await page.getByTestId('btn-redefinir-senha').click();
  await expect(page.getByTestId('erro-recuperacao')).toHaveText('A senha precisa ter ao menos 8 caracteres.');

  await page.getByTestId('input-nova-senha').fill('senhaNova123');
  await page.getByTestId('input-confirmar-senha').fill('outra-coisa');
  await page.getByTestId('btn-redefinir-senha').click();
  await expect(page.getByTestId('erro-recuperacao')).toHaveText('As senhas não conferem.');

  // tudo certo na tela, código errado no servidor: a mensagem é a DELE, sem dizer o motivo
  await page.getByTestId('input-confirmar-senha').fill('senhaNova123');
  await page.getByTestId('btn-redefinir-senha').click();
  await expect(page.getByTestId('erro-recuperacao')).toHaveText('Código inválido ou expirado.');
  await expect(page.getByTestId('senha-redefinida')).toHaveCount(0);
});

test('e-mail desconhecido: o app avança igual e nenhum e-mail sai (a tela não revela quem tem conta)', async ({ page }) => {
  const desconhecido = `ninguem-${ts}@onda.dev`;
  await abrirRecuperacao(page);
  await pedirCodigo(page, desconhecido);   // mesma tela, mesma conversa

  await page.waitForTimeout(1500);         // dá tempo de um envio indevido acontecer
  expect(recebeuEmail(desconhecido)).toBe(false);
});

test('fluxo completo: código do e-mail → senha nova → entra com ela; a antiga deixa de valer', async ({ page }) => {
  const antes = Date.now() - 1000;
  await abrirRecuperacao(page);
  await pedirCodigo(page, CLIENTE.email);
  const codigo = await lerCodigoRecebido(CLIENTE.email, antes);

  await page.getByTestId('input-codigo-recuperacao').fill(codigo);
  await page.getByTestId('input-nova-senha').fill(NOVA_SENHA);
  await page.getByTestId('input-confirmar-senha').fill(NOVA_SENHA);
  await page.getByTestId('btn-redefinir-senha').click();

  await expect(page.getByTestId('senha-redefinida')).toBeVisible({ timeout: 10000 });
  await expect(page.getByText('Senha redefinida!')).toBeVisible();

  // volta ao login pelo botão e entra com a senha NOVA
  await page.getByTestId('btn-ir-login').click();
  await page.getByPlaceholder('seu@email.com').fill(CLIENTE.email);
  await page.getByPlaceholder('••••••••').fill(NOVA_SENHA);
  await page.getByText('Entrar', { exact: true }).click();
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 10000 });
});

test('a senha ANTIGA não entra mais', async ({ page }) => {
  await page.goto('/');
  await page.getByText('Já tenho conta', { exact: true }).click();
  await page.getByPlaceholder('seu@email.com').fill(CLIENTE.email);
  await page.getByPlaceholder('••••••••').fill(CLIENTE.senha);
  await page.getByText('Entrar', { exact: true }).click();

  await expect(page.getByText('Credenciais inválidas.')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Criar pedido', { exact: true })).toHaveCount(0);
});
