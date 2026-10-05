import { test, expect, Page } from '@playwright/test';
import { fakeCpf } from './helpers/auth';

// Uma pessoa = um CPF (antifraude, Camada 2). O cadastro do prestador agora valida os dígitos e recusa CPF já vinculado a
// outra conta — antes só o cliente, no 1º pagamento, tinha o CPF único; o prestador recusado podia se recadastrar com o
// mesmo CPF. O backend tem E2E real; aqui se prova o que o USUÁRIO vê: a mensagem certa e o cadastro que não avança.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem isso o worker novo reavalia
// `ts` e o teste procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();
const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';
const CPF = fakeCpf(ts);

/** Preenche o formulário do prestador e envia — sem esperar sucesso: aqui o cadastro deve ser RECUSADO. */
async function enviarCadastro(page: Page, email: string, cpf: string) {
  await page.goto('/');
  await page.getByText('Sou Prestador', { exact: true }).click();
  await expect(page.getByText('NOME COMPLETO')).toBeVisible();
  await page.getByPlaceholder('José Wagner Ferreira').fill('Cadu Cpf');
  await page.getByPlaceholder('123.456.789-00').fill(cpf);
  await page.getByPlaceholder('jose@email.com').fill(email);
  await page.getByPlaceholder('mínimo 8 caracteres').fill('senha1234');
  await page.getByText('Elétrica', { exact: true }).click();
  await page.getByText(/Li e aceito os/i).click();
  await page.getByText('Enviar para verificação', { exact: true }).click();
}

test('setup: um prestador já cadastrado com esse CPF, pela API', async () => {
  const res = await fetch(`${API}/auth/register/provider`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome: 'Primeiro Dono', email: `primeiro-dono-${ts}@onda.dev`, senha: 'senha1234',
      cpf: CPF, categoria: 'Elétrica', aceitouTermos: true }),
  });
  expect(res.status, 'cadastro pela API').toBe(201);
});

test('CPF com dígito verificador errado é recusado com a mensagem do servidor', async ({ page }) => {
  await enviarCadastro(page, `cpf-invalido-${ts}@onda.dev`, '123.456.789-00');

  await expect(page.getByText('CPF inválido. Confira os números.')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Verificação em andamento')).toHaveCount(0);   // o cadastro não avançou
});

test('o mesmo CPF não faz uma segunda conta: a mensagem diz que já está vinculado', async ({ page }) => {
  await enviarCadastro(page, `segundo-dono-${ts}@onda.dev`, CPF);

  await expect(page.getByText('Este CPF já está vinculado a outra conta.')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Verificação em andamento')).toHaveCount(0);
});
