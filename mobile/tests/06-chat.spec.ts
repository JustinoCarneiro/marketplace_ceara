import { test, expect } from '@playwright/test';
import { registerCliente, registerPrestador, login, futureHorarioProposto } from './helpers/auth';

// Chat pré-transação entre cliente e prestador (docs/BOAS_PRATICAS_UX.md §1) — telefone e
// e-mail digitados são mascarados pelo backend antes de persistir (anti-desintermediação).
const ts = Date.now();

function fakeCpf(seed: number): string {
  const d = String(seed).slice(-9).padStart(9, '0');
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-00`;
}

const CLIENTE = { nome: 'Nina Chat', email: `nina-chat-${ts}@onda.dev`, senha: 'senha1234' };
const PRESTADOR = {
  nome: 'Theo Chat', cpf: fakeCpf(ts + 1),
  email: `theo-chat-${ts}@onda.dev`, senha: 'senha1234', categoria: 'Pintura',
};
const DESCRICAO = 'Preciso pintar a sala, cerca de 20m², parede com bolhas de umidade.';
const CLIENTE_CPF = fakeCpf(ts);

test('setup: cadastra cliente e prestador do fluxo de chat', async ({ browser }) => {
  const p1 = await browser.newPage();
  await registerCliente(p1, CLIENTE);
  await p1.close();

  const p2 = await browser.newPage();
  await registerPrestador(p2, PRESTADOR);
  await p2.close();
});

test('cliente e prestador trocam mensagens depois do aceite; telefone digitado é mascarado', async ({ page, browser }) => {
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Criar pedido', { exact: true }).click();
  await expect(page.getByText('Novo pedido')).toBeVisible({ timeout: 8000 });
  await page.getByText('Pintura', { exact: true }).last().click();
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
  await p2.getByTestId('input-valor').fill('300');
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

  // Cliente abre o chat (status ACEITO) e manda a primeira mensagem.
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  await expect(page.getByText('ACEITO', { exact: true }).last()).toBeVisible({ timeout: 8000 });
  await page.getByTestId('btn-abrir-chat').click();
  await expect(page.getByText('Conversa', { exact: true })).toBeVisible({ timeout: 8000 });
  await page.getByTestId('input-chat-mensagem').fill('Oi! Pode vir na quinta de manhã?');
  await page.getByTestId('btn-enviar-mensagem').click();
  await expect(page.getByText('Oi! Pode vir na quinta de manhã?')).toBeVisible({ timeout: 8000 });

  // Prestador abre o mesmo chat, vê a mensagem do cliente e responde com um telefone —
  // o texto que ele DIGITOU tem o número; o que aparece na tela (mascarado pelo backend)
  // não pode.
  await p2.getByText('Em Andamento', { exact: true }).click();
  await expect(p2.getByText('ACEITO', { exact: true })).toBeVisible({ timeout: 8000 });
  await p2.getByTestId('btn-abrir-chat').click();
  await expect(p2.getByText('Oi! Pode vir na quinta de manhã?').last()).toBeVisible({ timeout: 8000 });
  await p2.getByTestId('input-chat-mensagem').fill('Posso sim, me chama no (85) 99999-8888 se mudar algo');
  await p2.getByTestId('btn-enviar-mensagem').click();

  await expect(p2.getByText('contato removido por segurança').last()).toBeVisible({ timeout: 8000 });
  await expect(p2.getByText('99999-8888')).toHaveCount(0);

  // Cliente recarrega e enxerga a mesma versão mascarada — o dado sensível nunca existiu
  // do lado do backend, não é só escondido na tela de quem mandou.
  await login(page, CLIENTE.email, CLIENTE.senha);
  await page.getByText('Pedidos', { exact: true }).click();
  await page.getByTestId('meu-pedido-card').first().click();
  await page.getByTestId('btn-abrir-chat').click();
  await expect(page.getByText('contato removido por segurança').last()).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('99999-8888')).toHaveCount(0);

  await p2.close();
});
