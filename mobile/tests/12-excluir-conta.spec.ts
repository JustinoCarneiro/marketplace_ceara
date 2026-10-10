import { test, expect, Page } from '@playwright/test';
import { registerCliente, registerPrestador, login, fakeCpf } from './helpers/auth';
import { aprovarPrestador, criarServicoAceito } from './helpers/admin';

// Exclusão de conta (US36, LGPD art. 18, VI — e exigência da App Store e da Play Store). O backend
// tem E2E real contra o Postgres (cada tabela); aqui se prova o que o USUÁRIO vê: a tela explica o
// que acontece, a senha errada é recusada com a mensagem do backend, quem tem serviço em andamento
// descobre o que resolver, e a conta excluída não entra mais.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem isso o
// worker novo reavalia `ts` e o login procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();

const CLIENTE = { nome: 'Clara Saida', email: `clara-saida-${ts}@onda.dev`, senha: 'senha1234' };
const PRESTADOR = {
  nome: 'Paulo Preso', cpf: fakeCpf(ts),
  email: `paulo-preso-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Elétrica',
};

/** Login, aba Perfil e o link "Excluir minha conta": termina na tela de confirmação. */
async function abrirExclusao(page: Page, email: string, senha: string, aposLogin: string) {
  await login(page, email, senha);
  await expect(page.getByText(aposLogin)).toBeVisible({ timeout: 8000 });
  await page.getByText('Perfil', { exact: true }).click();
  await page.getByTestId('link-excluir-conta').click();
  await expect(page.getByTestId('input-senha-exclusao')).toBeVisible({ timeout: 8000 });
}

test('setup: cliente e prestador cadastrados, o prestador aprovado e um serviço ACEITO entre ele e outro cliente', async ({ browser }) => {
  const c = await browser.newPage();
  await registerCliente(c, CLIENTE);
  await c.close();

  const p = await browser.newPage();
  await registerPrestador(p, PRESTADOR);
  await p.close();

  await aprovarPrestador(PRESTADOR.email, PRESTADOR.senha);
  await criarServicoAceito(ts, PRESTADOR);
});

test('a tela explica o que acontece e pede a senha antes de qualquer coisa', async ({ page }) => {
  await abrirExclusao(page, CLIENTE.email, CLIENTE.senha, 'Criar pedido');

  await expect(page.getByText('Excluir conta', { exact: true })).toBeVisible();
  await expect(page.getByText(/seus dados pessoais .* são apagados/i)).toBeVisible();
  await expect(page.getByText(/histórico de pagamentos e as notas das avaliações ficam/i)).toBeVisible();
  await expect(page.getByText(/não dá para desfazer/i)).toBeVisible();

  // sem senha, nada é enviado: a própria tela barra
  await page.getByTestId('btn-excluir-conta').click();
  await expect(page.getByTestId('erro-exclusao')).toHaveText('Digite sua senha para confirmar.');
  await expect(page.getByTestId('conta-excluida')).toHaveCount(0);
});

test('senha errada: a conta não é excluída e o app mostra a mensagem do servidor', async ({ page }) => {
  await abrirExclusao(page, CLIENTE.email, CLIENTE.senha, 'Criar pedido');

  await page.getByTestId('input-senha-exclusao').fill('senha-errada-123');
  await page.getByTestId('btn-excluir-conta').click();

  await expect(page.getByTestId('erro-exclusao')).toHaveText('Senha incorreta.', { timeout: 8000 });
  await expect(page.getByTestId('conta-excluida')).toHaveCount(0);

  // a conta segue inteira: entra de novo sem problema
  await login(page, CLIENTE.email, CLIENTE.senha);
  await expect(page.getByText('Criar pedido', { exact: true })).toBeVisible({ timeout: 8000 });
});

test('prestador com serviço aceito não exclui: a tela diz o que resolver e a conta fica', async ({ page }) => {
  await abrirExclusao(page, PRESTADOR.email, PRESTADOR.senha, 'Pedidos disponíveis');

  await page.getByTestId('input-senha-exclusao').fill(PRESTADOR.senha);
  await page.getByTestId('btn-excluir-conta').click();

  await expect(page.getByTestId('erro-exclusao')).toContainText('aceitos, em andamento ou em disputa', { timeout: 8000 });
  await expect(page.getByTestId('conta-excluida')).toHaveCount(0);

  await login(page, PRESTADOR.email, PRESTADOR.senha);
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
});

test('cliente: senha certa exclui, confirma na tela, volta ao login e a conta não entra mais', async ({ page }) => {
  await abrirExclusao(page, CLIENTE.email, CLIENTE.senha, 'Criar pedido');

  await page.getByTestId('input-senha-exclusao').fill(CLIENTE.senha);
  await page.getByTestId('btn-excluir-conta').click();

  await expect(page.getByTestId('conta-excluida')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Conta excluída', { exact: true })).toBeVisible();
  await page.getByTestId('btn-concluir-exclusao').click();

  // sessão encerrada: volta para a tela inicial, deslogado
  await expect(page.getByText('Já tenho conta', { exact: true })).toBeVisible({ timeout: 8000 });

  // e as credenciais antigas não valem mais
  await login(page, CLIENTE.email, CLIENTE.senha);
  await expect(page.getByText('Credenciais inválidas.')).toBeVisible({ timeout: 8000 });
});
