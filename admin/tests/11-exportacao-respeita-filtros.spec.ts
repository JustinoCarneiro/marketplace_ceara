import { test, expect, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { loginAdmin } from './helpers/auth';

/**
 * US29: "exportar … respeitando os filtros aplicados". Até aqui o botão Exportar do dashboard
 * mandava só o bairro: quem filtrava "últimos 7 dias" baixava um PDF com o histórico inteiro,
 * sem aviso, e a tela de Relatórios nem tinha seletor de período. Os testes antigos de download
 * só conferiam o NOME do arquivo — por isso nada disso aparecia. Estes conferem a requisição
 * que sai da tela e o CONTEÚDO do arquivo baixado.
 *
 * O recorte em si (período, bairro, join da transação com o pedido) roda contra o Postgres no
 * E2E do backend (passo 19). Aqui é a ponta da tela: o que ela pede e o que chega no arquivo.
 */

/** Dispara `acao` e devolve a URL pedida ao endpoint de relatórios + o arquivo baixado. */
async function exportar(page: Page, acao: () => Promise<void>) {
  const [req, download] = await Promise.all([
    page.waitForRequest(r => r.url().includes('/admin/reports/')),
    page.waitForEvent('download'),
    acao(),
  ]);
  return { url: new URL(req.url()), download };
}

async function linhasDoCsv(download: { path(): Promise<string | null> }): Promise<string[]> {
  const caminho = await download.path();
  expect(caminho).toBeTruthy();
  return readFileSync(caminho!, 'utf8').trim().split('\n');
}

const DATA = /^\d{4}-\d{2}-\d{2}$/;

test.describe('Dashboard → Exportar PDF', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
  });

  test('o PDF pede o MESMO período que as métricas mostradas na tela', async ({ page }) => {
    const metricas: string[] = [];
    page.on('request', r => { if (r.url().includes('/admin/metrics')) metricas.push(r.url()); });

    await page.getByLabel('Período das métricas').selectOption('7');
    await expect.poll(() => metricas.at(-1) ?? '').toMatch(/[?&]de=\d{4}-\d{2}-\d{2}/);
    const naTela = new URL(metricas.at(-1)!).searchParams;

    const { url, download } = await exportar(page, () => page.getByRole('button', { name: /Exportar/ }).click());

    expect(url.pathname).toMatch(/\/reports\/metrics\.pdf$/);
    expect(url.searchParams.get('de'), 'o PDF tem que carregar o início do período da tela').toMatch(DATA);
    expect(url.searchParams.get('de')).toBe(naTela.get('de'));
    expect(url.searchParams.get('ate')).toBe(naTela.get('ate'));
    expect(download.suggestedFilename()).toBe('metrics.pdf');
  });

  test('"Todo o período" exporta sem datas', async ({ page }) => {
    await page.getByLabel('Período das métricas').selectOption('null');

    const { url } = await exportar(page, () => page.getByRole('button', { name: /Exportar/ }).click());

    expect(url.pathname).toMatch(/\/reports\/metrics\.pdf$/);
    expect(url.searchParams.has('de')).toBe(false);
    expect(url.searchParams.has('ate')).toBe(false);
  });
});

test.describe('Relatórios → filtros no arquivo', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
    await page.goto('/reports');
    await expect(page.getByTestId('select-periodo-relatorio')).toBeVisible();
  });

  test('a tela oferece o período (e não diz mais que traz o histórico completo)', async ({ page }) => {
    await expect(page.getByTestId('select-periodo-relatorio')).toHaveValue('null');
    await expect(page.getByText(/sem filtro de período/i)).toHaveCount(0);
    await expect(page.getByTestId('select-periodo-relatorio').locator('option')).toHaveText(
      ['Últimos 7 dias', 'Últimos 30 dias', 'Últimos 90 dias', 'Todo o período']);
  });

  test('CSV de pedidos com "últimos 7 dias": pede de/ate e o arquivo só tem linhas dentro da janela', async ({ page }) => {
    await page.getByTestId('select-periodo-relatorio').selectOption('7');

    const { url, download } = await exportar(page, () =>
      page.getByRole('button', { name: /Gerar relatório/i }).click());

    expect(url.pathname).toMatch(/\/reports\/requests\.csv$/);
    const de = url.searchParams.get('de')!;
    const ate = url.searchParams.get('ate')!;
    expect(de).toMatch(DATA);
    expect(ate).toMatch(DATA);

    const linhas = await linhasDoCsv(download);
    expect(linhas[0]).toBe('id,categoria,bairro,status,criadoEm');
    // dia de Fortaleza (UTC-3); "ate" entra inteiro, então o limite é o começo do dia seguinte
    const inicio = new Date(`${de}T00:00:00-03:00`).getTime();
    const fim = new Date(`${ate}T00:00:00-03:00`).getTime() + 24 * 60 * 60 * 1000;
    for (const linha of linhas.slice(1)) {
      const criadoEm = new Date(linha.split(',').at(-1)!).getTime();
      expect(criadoEm, `linha fora da janela ${de}..${ate}: ${linha}`).toBeGreaterThanOrEqual(inicio);
      expect(criadoEm, `linha fora da janela ${de}..${ate}: ${linha}`).toBeLessThan(fim);
    }
  });

  test('"Todo o período" não manda datas e o CSV traz os pedidos existentes (nada some)', async ({ page }) => {
    const { url, download } = await exportar(page, () =>
      page.getByRole('button', { name: /Gerar relatório/i }).click());

    expect(url.searchParams.has('de')).toBe(false);
    expect(url.searchParams.has('ate')).toBe(false);
    const linhas = await linhasDoCsv(download);
    expect(linhas[0]).toBe('id,categoria,bairro,status,criadoEm');
    // O SeedRunner cria um pedido (que a suíte de disputas depois resolve — por isso não se
    // confere o status, só que ele continua no arquivo). Sem esta linha, o teste da janela de
    // 7 dias poderia passar por arquivo vazio.
    expect(linhas.length).toBeGreaterThan(1);
  });

  test('formato Transações baixa transacoes.csv com o período escolhido', async ({ page }) => {
    await page.getByTestId('opcao-transacoes').click();
    await page.getByTestId('select-periodo-relatorio').selectOption('30');

    const { url, download } = await exportar(page, () =>
      page.getByRole('button', { name: /Gerar relatório/i }).click());

    expect(url.pathname).toMatch(/\/reports\/transactions\.csv$/);
    expect(url.searchParams.get('de')).toMatch(DATA);
    expect(url.searchParams.get('ate')).toMatch(DATA);
    expect(download.suggestedFilename()).toBe('transacoes.csv');
    const linhas = await linhasDoCsv(download);
    expect(linhas[0]).toBe('id,serviceRequestId,valorTotal,valorComissao,metodo,statusPagamento,criadoEm');
  });

  test('PDF a partir de Relatórios também leva o período', async ({ page }) => {
    await page.getByText('PDF', { exact: true }).click();
    await page.getByTestId('select-periodo-relatorio').selectOption('90');

    const { url, download } = await exportar(page, () =>
      page.getByRole('button', { name: /Gerar relatório/i }).click());

    expect(url.pathname).toMatch(/\/reports\/metrics\.pdf$/);
    expect(url.searchParams.get('de')).toMatch(DATA);
    expect(url.searchParams.get('ate')).toMatch(DATA);
    expect(download.suggestedFilename()).toBe('metrics.pdf');
  });
});
