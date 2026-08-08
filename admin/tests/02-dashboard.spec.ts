import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
  });

  test('exibe título e KPIs financeiros', async ({ page }) => {
    await expect(page.getByText(/Dashboard/i).first()).toBeVisible();
    await expect(page.getByText(/GMV/i)).toBeVisible();
    await expect(page.getByText(/Receita comissão/i)).toBeVisible();
    await expect(page.getByText(/Ticket médio/i)).toBeVisible();
    await expect(page.getByText(/Taxa de conclusão/i)).toBeVisible();
  });

  test('exibe métricas de operações', async ({ page }) => {
    await expect(page.getByText(/Disputas abertas/i)).toBeVisible();
    await expect(page.getByText(/SOS acionados/i)).toBeVisible();
  });

  test('exibe métricas de usuários', async ({ page }) => {
    await expect(page.getByText(/Clientes ativos/i)).toBeVisible();
    await expect(page.getByText(/Prestadores verificados/i)).toBeVisible();
  });

  test('tabela de pedidos por status é renderizada', async ({ page }) => {
    await expect(page.getByText(/Pedidos por status/i)).toBeVisible();
  });

  test('sidebar exibe todos os itens de navegação', async ({ page }) => {
    // Itens de navegação são links (NavLink) — usa role p/ não colidir com textos da página
    for (const label of [
      /Dashboard/, /Disputas/, /Moderação/, /Usuários/, /Financeiro/,
      /Categorias/, /Auditoria/, /Notificações/, /Relatórios/,
    ]) {
      await expect(page.getByRole('link', { name: label }).first()).toBeVisible();
    }
  });
});

/**
 * Os testes acima só conferiam RÓTULOS. Por isso a tela passou meses verde enquanto
 * o backend não devolvia gmv/ticketMedio/taxaConclusao/clientesAtivos/sosAcionados —
 * os cards renderizavam "R$ NaN"/0 e nada acusava. Aqui checamos os VALORES.
 */
test.describe('Dashboard (valores reais, não só rótulos)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAdmin(page);
  });

  test('nenhum KPI renderiza NaN, undefined ou vazio', async ({ page }) => {
    const kpis = ['kpi-gmv', 'kpi-comissao', 'kpi-ticket', 'kpi-taxa',
                  'kpi-disputas', 'kpi-sos', 'kpi-prestadores', 'kpi-clientes'];

    for (const id of kpis) {
      const texto = (await page.getByTestId(id).textContent())?.trim() ?? '';
      expect(texto, `${id} veio vazio`).not.toBe('');
      expect(texto, `${id} renderizou valor inválido: "${texto}"`)
        .not.toMatch(/NaN|undefined|null/i);
    }
  });

  test('KPIs financeiros vêm formatados como moeda e a taxa como percentual', async ({ page }) => {
    await expect(page.getByTestId('kpi-gmv')).toHaveText(/^R\$\s?[\d.,]+$/);
    await expect(page.getByTestId('kpi-comissao')).toHaveText(/^R\$\s?[\d.,]+$/);
    await expect(page.getByTestId('kpi-ticket')).toHaveText(/^R\$\s?[\d.,]+$/);
    await expect(page.getByTestId('kpi-taxa')).toHaveText(/^\d+([.,]\d+)?%$/);
  });

  test('reflete a disputa semeada e a transação retida do SeedRunner', async ({ page }) => {
    // O SeedRunner cria 1 pedido EM_DISPUTA com transação RETIDO de R$ 250 —
    // então disputas abertas > 0 e o GMV precisa contar esse valor retido.
    await expect(page.getByTestId('kpi-disputas')).not.toHaveText('0');

    const gmv = (await page.getByTestId('kpi-gmv').textContent()) ?? '';
    const valor = Number(gmv.replace(/[^\d,]/g, '').replace(',', '.'));
    expect(valor, `GMV deveria contar o escrow retido do seed, veio "${gmv}"`).toBeGreaterThan(0);
  });

  test('gráfico de pedidos por status mostra as contagens reais da API', async ({ page }) => {
    // Antes o gráfico caía num fallback inventado (10/6/4, total 30) quando o campo
    // faltava — desenhava barras convincentes sobre nada.
    const disputa = page.getByTestId('barra-EM_DISPUTA');
    await expect(disputa).toBeVisible();
    await expect(disputa).toHaveAttribute('title', /Disputa: [1-9]\d*/);
  });

  test('trocar o período recarrega as métricas do backend', async ({ page }) => {
    const chamadas: string[] = [];
    page.on('request', r => {
      if (r.url().includes('/admin/metrics')) chamadas.push(r.url());
    });

    await page.getByLabel('Período das métricas').selectOption('7');
    await expect.poll(() => chamadas.length).toBeGreaterThan(0);
    expect(chamadas.at(-1)).toMatch(/[?&]de=\d{4}-\d{2}-\d{2}/);

    // "Todo o período" consulta sem recorte de data
    await page.getByLabel('Período das métricas').selectOption('null');
    await expect.poll(() => chamadas.at(-1)).not.toMatch(/[?&]de=/);
    await expect(page.getByTestId('kpi-gmv')).toBeVisible();
  });
});
