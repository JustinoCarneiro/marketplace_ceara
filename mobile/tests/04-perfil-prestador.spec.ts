import { test, expect } from '@playwright/test';
import { registerCliente } from './helpers/auth';

// Cobre o caminho "tocar num prestador da busca → ver perfil" — até 2026-08-12 esse caminho
// não tinha NENHUM E2E: os fluxos de pedido (02, 03) criam o pedido por categoria direto
// (NewRequestScreen), nunca passam pelo card de um prestador específico. Foi exatamente esse
// ponto cego que deixou sobreviver o bug de GET /providers/nearby devolvendo o PK errado —
// GET /providers/{id} sempre respondia 422 e nenhum teste percebia (ver memória
// nearby-id-vs-userid-2026-08-12).
//
// Usa a prestadora semeada (Ana Eletricista, VERIFICADO + localização em Aldeota) porque um
// prestador criado dinamicamente por um teste fica sempre EM_VERIFICACAO — nunca aparece em
// /providers/nearby (a query exige VERIFICADO), então não tem como alcançar esta tela pela
// navegação real com um prestador criado na hora.
const ts = Date.now();
const CLIENTE = { nome: 'Bia Perfil', email: `bia-perfil-${ts}@onda.dev`, senha: 'senha1234' };

test('cliente toca num prestador da Home e abre o perfil público dele', async ({ page }) => {
  await registerCliente(page, CLIENTE);

  // Home carrega "Próximos de você" automaticamente (raio 8km da geolocalização fixada no
  // playwright.config.ts, que é a mesma coordenada onde Ana Eletricista foi semeada).
  await expect(page.getByText('Ana Eletricista')).toBeVisible({ timeout: 10000 });
  await page.getByText('Ana Eletricista').click();

  // Antes do fix de 2026-08-12, este GET /providers/{id} sempre voltava 422 e a tela
  // ficava presa em "Perfil não encontrado." — se a regressão voltar, é aqui que quebra.
  await expect(page.getByText('Perfil não encontrado.')).not.toBeVisible();
  // .last(): react-native-screens mantém a Home montada (hidden) atrás — "Ana Eletricista"
  // aparece nas duas telas, a de cima (ProviderProfile) é a que vem depois no DOM. Mesmo
  // motivo do .last() nos testes 02/03.
  await expect(page.getByText('Ana Eletricista').last()).toBeVisible({ timeout: 8000 });
  await expect(page.getByText('PRESTADOR VERIFICADO')).toBeVisible();
  await expect(page.getByText('Solicitar serviço')).toBeVisible();
});
