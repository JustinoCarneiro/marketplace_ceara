import { test, expect } from '@playwright/test';
import { registerCliente, registerPrestador, login } from './helpers/auth';

// Um cliente e um prestador dedicados a este arquivo — os testes rodam em sequência
// (ordem de declaração, workers:1) e compartilham o único pedido criado no 2º teste.
const ts = Date.now();

// users.cpf_hash é UNIQUE (antifraude Camada 2) — CPF fixo colide entre reruns contra o
// mesmo banco (ficou vinculado à conta da rodada anterior). Sem checksum exigido pelo
// backend (só formato), deriva do timestamp pra nunca repetir; +1 garante que cliente e
// prestador nunca coincidem entre si.
function fakeCpf(seed: number): string {
  const d = String(seed).slice(-9).padStart(9, '0');
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-00`;
}

const CLIENTE = { nome: 'Lucia Fluxo', email: `lucia-fluxo-${ts}@onda.dev`, senha: 'senha1234' };
const PRESTADOR = {
  nome: 'Jose Fluxo', cpf: fakeCpf(ts + 1),
  email: `jose-fluxo-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Elétrica',
};
const DESCRICAO = 'Tomada da cozinha solta faísca ao ligar a air fryer, preciso trocar com segurança.';
const CLIENTE_CPF = fakeCpf(ts);

test('setup: cadastra o cliente e o prestador do fluxo', async ({ browser }) => {
  const p1 = await browser.newPage();
  await registerCliente(p1, CLIENTE);
  await p1.close();

  const p2 = await browser.newPage();
  await registerPrestador(p2, PRESTADOR);
  await p2.close();
});

test('cliente cria o pedido e publica (revisão com IA / fallback manual)', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Criar pedido', { exact: true }).click();
  await expect(page.getByText('Novo pedido')).toBeVisible({ timeout: 8000 });

  // react-native-screens mantém a Home montada por trás (transição de stack) — "Elétrica"
  // aparece nos dois, a tela de cima (NewRequestScreen) vem depois no DOM.
  await page.getByText('Elétrica', { exact: true }).last().click();
  await page.getByPlaceholder(/A tomada da cozinha solta faísca/).fill(DESCRICAO);
  await page.getByText('Continuar', { exact: true }).click();

  // AiAssistantScreen: "Analisando seu pedido…" -> formulário com o CTA de publicar.
  // Fallback manual garante que o CTA aparece mesmo se a IA não sugerir nada (CLAUDE.md).
  await expect(page.getByText('Confirmar e publicar pedido')).toBeVisible({ timeout: 15000 });
  await page.getByText('Confirmar e publicar pedido', { exact: true }).click();

  await expect(page.getByText('Pedido criado!')).toBeVisible({ timeout: 10000 });
});

test('pedido aparece em Meus Pedidos como PENDENTE', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await expect(page.getByTestId('meu-pedido-card').first()).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('Elétrica').first()).toBeVisible();
});

test('prestador vê o pedido em Disponíveis e envia proposta', async ({ page }) => {
  await login(page, PRESTADOR.email, PRESTADOR.senha);
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });

  await page.getByTestId('btn-propor').first().click();
  await expect(page.getByText('SEU VALOR')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-valor').fill('150');
  // prazo já vem preenchido com "2" por padrão — só envia.
  await page.getByTestId('btn-enviar-proposta').click();

  // A folha fecha e volta pra lista ao enviar com sucesso.
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
});

test('cliente aceita a proposta e paga com Pix (confirmando identidade no 1º pagamento)', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  // Sem exact: bate também na sublabel da timeline "PROPOSTO → ACEITO" (prefixo igual).
  await expect(page.getByText('PROPOSTO', { exact: true })).toBeVisible({ timeout: 8000 });

  await page.getByTestId('btn-ver-propostas').click();
  await expect(page.getByText('Aceitar proposta').first()).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-aceitar-proposta').first().click();

  await expect(page.getByTestId('btn-pagar')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-pagar').click();

  // Antifraude Camada 2: 1º pagamento exige confirmar identidade (CPF) antes de seguir.
  await expect(page.getByText('Confirme sua identidade')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-cpf').fill(CLIENTE_CPF);
  await page.getByTestId('btn-confirmar-cpf').click();

  await expect(page.getByTestId('btn-paguei')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-paguei').click();

  await expect(page.getByText('Pagamento retido com segurança')).toBeVisible({ timeout: 8000 });
});

test('prestador inicia o serviço pela tab "Em Andamento"', async ({ page }) => {
  await login(page, PRESTADOR.email, PRESTADOR.senha);
  await page.getByText('Em Andamento', { exact: true }).click();

  // ActiveJobScreen resolve o requestId (GET /service-requests/active) e empurra
  // pro RequestDetail sozinho — sem isso a tab não tinha como abrir nada.
  // Sem exact: bate também na sublabel da timeline "PROPOSTO → ACEITO".
  await expect(page.getByText('ACEITO', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Iniciar serviço', { exact: true }).click();
  // getByText é case-insensitive por padrão: sem exact bate na tab "Em Andamento" e na
  // sublabel da timeline "Serviço em andamento".
  await expect(page.getByText('EM ANDAMENTO', { exact: true })).toBeVisible({ timeout: 8000 });
});

test('cliente confirma a conclusão e avalia o prestador', async ({ page }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  // O card em MyRequestsScreen (agora escondido atrás, via react-native-screens) tem seu
  // próprio badge "EM ANDAMENTO" — bate igual mesmo com exact. RequestDetailScreen (topo
  // da pilha) vem depois no DOM.
  await expect(page.getByText('EM ANDAMENTO', { exact: true }).last()).toBeVisible({ timeout: 8000 });

  await page.getByText('Confirmar conclusão', { exact: true }).click();
  // Confirmar conclusão só muda o status — "Avaliar" é uma ação separada.
  await expect(page.getByText('Avaliar', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByText('Avaliar', { exact: true }).click();

  await expect(page.getByText(/Como foi o serviço/)).toBeVisible({ timeout: 8000 });
  await page.getByText('★', { exact: true }).nth(4).click(); // 5ª estrela
  await page.getByText('Enviar avaliação', { exact: true }).click();

  await expect(page.getByText('Obrigado!')).toBeVisible({ timeout: 8000 });
});
