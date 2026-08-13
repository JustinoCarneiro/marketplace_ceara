import { Page, expect } from '@playwright/test';

// react-native-web renderiza TouchableOpacity como <div tabindex="0"> SEM role="button" —
// getByRole('button', ...) não funciona aqui (confirmado via probe no DOM real). Todo clique
// em CTA usa getByText.

export interface ClienteSeed {
  nome: string;
  email: string;
  senha: string;
}

export interface PrestadorSeed {
  nome: string;
  cpf: string;
  email: string;
  senha: string;
  categoria: string;
}

/** Splash -> "Sou Cliente" -> preenche form -> aceita termos -> "Criar conta". Deixa logado. */
export async function registerCliente(page: Page, c: ClienteSeed) {
  await page.goto('/');
  await page.getByText('Sou Cliente', { exact: true }).click();
  await expect(page.getByText('NOME COMPLETO')).toBeVisible();

  await page.getByPlaceholder('Lúcia Maria Alves').fill(c.nome);
  await page.getByPlaceholder('lucia.alves@email.com').fill(c.email);
  await page.getByPlaceholder('mínimo 8 caracteres').fill(c.senha);
  await page.getByText(/Li e aceito os/i).click();
  await page.getByText('Criar conta', { exact: true }).click();
}

/**
 * Splash -> "Sou Prestador" -> preenche form -> escolhe categoria -> "Enviar para verificação".
 * O cadastro sempre cai na tela EM VERIFICAÇÃO (bloqueada, só "Sair") — o backend não exige
 * verificação pra usar o app (browsing/proposta funcionam em EM_VERIFICACAO), então a função
 * já sai e loga de novo pra devolver uma sessão utilizável, igual um prestador real faria.
 */
export async function registerPrestador(page: Page, p: PrestadorSeed) {
  await page.goto('/');
  await page.getByText('Sou Prestador', { exact: true }).click();
  await expect(page.getByText('NOME COMPLETO')).toBeVisible();

  await page.getByPlaceholder('José Wagner Ferreira').fill(p.nome);
  await page.getByPlaceholder('123.456.789-00').fill(p.cpf);
  await page.getByPlaceholder('jose@email.com').fill(p.email);
  await page.getByPlaceholder('mínimo 8 caracteres').fill(p.senha);
  await page.getByText(p.categoria, { exact: true }).click();
  await page.getByText(/Li e aceito os/i).click();
  await page.getByText('Enviar para verificação', { exact: true }).click();

  // "EM VERIFICAÇÃO" sozinho é ambíguo: o rodapé do PRÓPRIO formulário já promete esse badge
  // antes de enviar ("Depois de enviar, seu cadastro fica EM VERIFICAÇÃO") — checar só esse
  // texto não prova que a navegação pra VerificationScreen aconteceu de verdade. O heading
  // "Verificação em andamento" só existe lá.
  await expect(page.getByText('Verificação em andamento')).toBeVisible({ timeout: 8000 });
  await page.getByText('Sair', { exact: true }).click();
  await login(page, p.email, p.senha);
  // Falha aqui, no helper, deixa claro que o cadastro/login do prestador não funcionou —
  // em vez de um "Credenciais inválidas" confuso aparecendo bem mais tarde, num teste alheio.
  await expect(page.getByText('Pedidos disponíveis')).toBeVisible({ timeout: 8000 });
}

/** Splash -> "Já tenho conta" -> login. */
export async function login(page: Page, email: string, senha: string) {
  await page.goto('/');
  await page.getByText('Já tenho conta', { exact: true }).click();
  await page.getByPlaceholder('seu@email.com').fill(email);
  await page.getByPlaceholder('••••••••').fill(senha);
  await page.getByText('Entrar', { exact: true }).click();
}

/**
 * Data/hora amanhã às 14h, no formato DD/MM/AAAA + HH:MM que SendProposalScreen espera —
 * backend exige horarioProposto no futuro (@Future), e "amanhã" evita flakiness de rodar o
 * teste perto da meia-noite (hoje + poucas horas podia cair no passado por causa do fuso).
 */
export function futureHorarioProposto(): { data: string; hora: string } {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const dia = String(d.getDate()).padStart(2, '0');
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  return { data: `${dia}/${mes}/${d.getFullYear()}`, hora: '14:00' };
}
