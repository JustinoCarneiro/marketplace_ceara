import { test, expect } from '@playwright/test';
import { registerPrestador, login, futureHorarioProposto, fakeCpf } from './helpers/auth';
import { aprovarPrestador, criarPedidoPendente } from './helpers/admin';

// Só prestador VERIFICADO envia proposta (ProviderVerificationGuard). Até aqui a "aprovação manual"
// do admin só mudava um status no banco: quem acabava de se cadastrar propunha e recebia do mesmo
// jeito. Este fluxo prova o que o PRESTADOR vê: o motivo na própria folha de proposta e, depois da
// aprovação do admin, o mesmo envio passando.
// Serial: no retry o grupo inteiro roda de novo (ver 02-fluxo-pedido-completo.spec.ts) — sem isso o
// worker novo reavalia `ts` e o login procura uma conta que nunca foi criada.
test.describe.configure({ mode: 'serial' });
const ts = Date.now();

const PRESTADOR = {
  nome: 'Vera Verificacao', cpf: fakeCpf(ts),
  email: `vera-verificacao-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Elétrica',
};

test('setup: um pedido pendente (pela API) e o prestador cadastrado pela tela, ainda em verificação', async ({ browser }) => {
  await criarPedidoPendente(ts);

  const p = await browser.newPage();
  await registerPrestador(p, PRESTADOR);
  await p.close();
});

test('em verificação o app mostra o motivo da recusa; depois de aprovado, o mesmo envio passa', async ({ page }) => {
  await login(page, PRESTADOR.email, PRESTADOR.senha);
  // Em verificação ele continua vendo os pedidos: só não consegue propor.
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-propor').first().click();
  await expect(page.getByText('SEU VALOR')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-valor').fill('180');
  const horario = futureHorarioProposto();
  await page.getByTestId('input-data-proposta').fill(horario.data);
  await page.getByTestId('input-hora-proposta').fill(horario.hora);
  await page.getByTestId('btn-enviar-proposta').click();

  // Recusa com o motivo, na própria folha — que continua aberta para reenviar depois.
  await expect(page.getByText(/Seu cadastro ainda está em verificação/)).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('SEU VALOR')).toBeVisible();

  // O admin aprova (mesmo caminho do botão "Verificar" do painel) e o MESMO envio passa.
  await aprovarPrestador(PRESTADOR.email, PRESTADOR.senha);
  await page.getByTestId('btn-enviar-proposta').click();
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  await expect(page.getByText(/Seu cadastro ainda está em verificação/)).toHaveCount(0);
});
