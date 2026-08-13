import { test, expect } from '@playwright/test';
import { registerCliente, registerPrestador, login, futureHorarioProposto } from './helpers/auth';

// Até hoje, "Abrir disputa" não tinha NENHUM botão em lugar nenhum do app — o botão sob a
// mesma condição de visibilidade (showDispute) navegava pro SOS, sem relação com disputa
// nenhuma. Backend, OpenDisputeScreen e a fila de mediação do admin já existiam prontos e
// funcionando, só não tinham como ser alcançados pela navegação real (ver memória
// nearby-id-vs-userid-2026-08-12 — mesma classe de achado: código pronto, gatilho ausente).
const ts = Date.now();

function fakeCpf(seed: number): string {
  const d = String(seed).slice(-9).padStart(9, '0');
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-00`;
}

const CLIENTE = { nome: 'Dora Disputa', email: `dora-disputa-${ts}@onda.dev`, senha: 'senha1234' };
const PRESTADOR = {
  nome: 'Caio Disputa', cpf: fakeCpf(ts + 1),
  email: `caio-disputa-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Hidráulica',
};
const DESCRICAO = 'Vazamento no cano da pia, água acumulando embaixo do armário.';
const CLIENTE_CPF = fakeCpf(ts);

test('setup: cadastra cliente e prestador do fluxo de disputa', async ({ browser }) => {
  const p1 = await browser.newPage();
  await registerCliente(p1, CLIENTE);
  await p1.close();

  const p2 = await browser.newPage();
  await registerPrestador(p2, PRESTADOR);
  await p2.close();
});

test('cliente abre disputa pelo app e o pedido some da tab "Em Andamento" do prestador', async ({ page, browser }) => {
  // Chega o pedido até EM_ANDAMENTO (mesmo caminho de 02-fluxo-pedido-completo.spec.ts).
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Criar pedido', { exact: true }).click();
  await expect(page.getByText('Novo pedido')).toBeVisible({ timeout: 8000 });
  await page.getByText('Hidráulica', { exact: true }).last().click();
  await page.getByPlaceholder(/A tomada da cozinha solta faísca/).fill(DESCRICAO);
  await page.getByText('Continuar', { exact: true }).click();
  await expect(page.getByText('Confirmar e publicar pedido')).toBeVisible({ timeout: 15000 });
  await page.getByText('Confirmar e publicar pedido', { exact: true }).click();
  await expect(page.getByText('Pedido criado!')).toBeVisible({ timeout: 10000 });

  const p2 = await browser.newPage();
  await login(p2, PRESTADOR.email, PRESTADOR.senha);
  await expect(p2.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
  await p2.getByTestId('btn-propor').first().click();
  await expect(p2.getByText('SEU VALOR')).toBeVisible({ timeout: 8000 });
  await p2.getByTestId('input-valor').fill('180');
  const horario = futureHorarioProposto();
  await p2.getByTestId('input-data-proposta').fill(horario.data);
  await p2.getByTestId('input-hora-proposta').fill(horario.hora);
  await p2.getByTestId('btn-enviar-proposta').click();
  await expect(p2.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });

  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  await expect(page.getByText('PROPOSTO', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-ver-propostas').click();
  await expect(page.getByText('Aceitar proposta').first()).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-aceitar-proposta').first().click();
  await expect(page.getByTestId('btn-pagar')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-pagar').click();
  await expect(page.getByText('Confirme sua identidade')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-cpf').fill(CLIENTE_CPF);
  await page.getByTestId('btn-confirmar-cpf').click();
  await expect(page.getByTestId('btn-paguei')).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-paguei').click();
  await expect(page.getByText('Pagamento retido com segurança')).toBeVisible({ timeout: 8000 });

  await p2.getByText('Em Andamento', { exact: true }).click();
  await expect(p2.getByText('ACEITO', { exact: true })).toBeVisible({ timeout: 8000 });
  await p2.getByText('Iniciar serviço', { exact: true }).click();
  await expect(p2.getByText('EM ANDAMENTO', { exact: true })).toBeVisible({ timeout: 8000 });

  // Cliente abre a disputa pela primeira vez pela UI real.
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  await expect(page.getByText('EM ANDAMENTO', { exact: true }).last()).toBeVisible({ timeout: 8000 });

  await page.getByTestId('btn-abrir-disputa').click();
  // "Abrir disputa" aparece 3x na árvore nesse momento (botão da RequestDetail escondida +
  // header e botão da OpenDisputeScreen) — "Qual o motivo?" só existe na tela nova.
  await expect(page.getByText('Qual o motivo?')).toBeVisible({ timeout: 8000 });
  await page.getByText('Profissional não compareceu', { exact: true }).click();
  await page.getByPlaceholder(/desligou a energia/).fill('Marcou o horário e não apareceu, não responde mensagem.');
  await page.getByText('Abrir disputa', { exact: true }).last().click();

  // useFocusEffect refaz a busca ao voltar — sem isso a tela continuaria presa em
  // EM_ANDAMENTO mesmo com a disputa já registrada de verdade no backend.
  await expect(page.getByText('EM DISPUTA', { exact: true }).last()).toBeVisible({ timeout: 8000 });

  // Efeito colateral esperado: a tab "Em Andamento" do prestador não resolve mais nenhum
  // atendimento ativo (GET /service-requests/active só considera ACEITO/EM_ANDAMENTO). p2
  // está empilhado no RequestDetail (a tab bar fica escondida atrás) — login() reseta a
  // pilha de volta pra Home antes de navegar pelas tabs de novo.
  await login(p2, PRESTADOR.email, PRESTADOR.senha);
  await p2.getByText('Em Andamento', { exact: true }).click();
  await expect(p2.getByText('Nenhum atendimento em andamento')).toBeVisible({ timeout: 8000 });

  await p2.close();
});
