import { test, expect, Page } from '@playwright/test';
import { registerPrestador, login } from './helpers/auth';

// Chave Pix do prestador (ChavePixScreen, MKT-49): é pra ela que o repasse vai quando o
// serviço fecha. Até aqui só o backend tinha teste (PixKeyTest, ProviderServiceTest); a tela
// — mensagem de erro do backend, estado "já cadastrada", chave nunca devolvida — não.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem
// isso o worker novo reavalia `ts` e o login procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();

function fakeCpf(seed: number): string {
  const d = String(seed).slice(-9).padStart(9, '0');
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-00`;
}

const PRESTADOR = {
  nome: 'Pedro Pix', cpf: fakeCpf(ts),
  email: `pedro-pix-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Elétrica',
};

// Mesmo CPF fictício do PixKeyTest: dígitos verificadores corretos / com o último errado.
const CHAVE_CPF_VALIDA = '111.444.777-35';
const CHAVE_CPF_DV_ERRADO = '111.444.777-36';
// Erro clássico de quem digita o celular: sem o +55 a chave não vale (e 11 dígitos soltos
// são lidos como CPF, cujo dígito verificador não bate).
const CHAVE_TELEFONE_SEM_55 = '85999990000';
const MENSAGEM_INVALIDA = /Chave Pix inválida\. Use CPF, CNPJ, e-mail, telefone com \+55 e DDD/;

async function abrirChavePix(page: Page) {
  await login(page, PRESTADOR.email, PRESTADOR.senha);
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  await page.getByText('Perfil', { exact: true }).click();
  await page.getByText('Chave Pix', { exact: true }).click();
  await expect(page.getByTestId('input-chave-pix')).toBeVisible({ timeout: 8000 });
}

test('setup: cadastra o prestador', async ({ page }) => {
  await registerPrestador(page, PRESTADOR);
});

test('chave vazia ou malformada é recusada — com a mensagem do backend — e nada é salvo', async ({ page }) => {
  await abrirChavePix(page);
  // Prestador novo: ainda sem chave, nem o selo de "já cadastrada".
  await expect(page.getByText('SUA CHAVE PIX', { exact: true })).toBeVisible();
  await expect(page.getByText('Você já tem uma chave Pix cadastrada.')).toHaveCount(0);

  // Vazia: barrada na própria tela, sem chamar o backend.
  await page.getByTestId('btn-salvar-chave-pix').click();
  await expect(page.getByText('Informe sua chave Pix.')).toBeVisible();

  // Malformada: o backend recusa (PIX_KEY_INVALID) e a tela mostra a mensagem DELE. Antes de
  // 2026-09-29 qualquer texto era aceito e o erro só apareceria na hora do repasse.
  for (const invalida of [CHAVE_CPF_DV_ERRADO, CHAVE_TELEFONE_SEM_55]) {
    await page.getByTestId('input-chave-pix').fill(invalida);
    await page.getByTestId('btn-salvar-chave-pix').click();
    await expect(page.getByText(MENSAGEM_INVALIDA)).toBeVisible({ timeout: 8000 });
    await expect(page.getByText('Chave Pix salva.')).toHaveCount(0);
    await expect(page.getByText('Você já tem uma chave Pix cadastrada.')).toHaveCount(0);
  }
});

test('chave válida é salva e a tela nunca mostra a chave de volta', async ({ page }) => {
  await abrirChavePix(page);
  // Sessão nova: as tentativas inválidas do teste anterior não salvaram nada.
  await expect(page.getByText('SUA CHAVE PIX', { exact: true })).toBeVisible();

  await page.getByTestId('input-chave-pix').fill(CHAVE_CPF_VALIDA);
  await page.getByTestId('btn-salvar-chave-pix').click();

  await expect(page.getByText('Chave Pix salva.')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Você já tem uma chave Pix cadastrada.')).toBeVisible();
  await expect(page.getByText('NOVA CHAVE PIX (SUBSTITUI A ATUAL)', { exact: true })).toBeVisible();
  // Cifrada em repouso e "nunca exibida de volta" (LGPD): o campo é limpo e o texto da chave
  // não aparece em lugar nenhum da tela.
  await expect(page.getByTestId('input-chave-pix')).toHaveValue('');
  await expect(page.getByText(CHAVE_CPF_VALIDA)).toHaveCount(0);
  await expect(page.getByText('11144477735')).toHaveCount(0);
});

test('a chave continua cadastrada em outra sessão e pode ser substituída', async ({ page }) => {
  await abrirChavePix(page);
  // Login novo: o estado vem do backend (GET /providers/me/chave-pix), não da memória da tela.
  await expect(page.getByText('Você já tem uma chave Pix cadastrada.')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('NOVA CHAVE PIX (SUBSTITUI A ATUAL)', { exact: true })).toBeVisible();

  await page.getByTestId('input-chave-pix').fill(`pedro.pix.${ts}@onda.dev`);
  await page.getByTestId('btn-salvar-chave-pix').click();
  await expect(page.getByText('Chave Pix salva.')).toBeVisible({ timeout: 8000 });
});
