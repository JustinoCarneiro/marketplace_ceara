import { test, expect, Page } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

/**
 * Conciliar CPF no painel (duplicata legada de CPF). O backend já tinha a ação `CONCILIAR_CPF`; faltava a tela — sem ela só um
 * `curl` desfazia a marca `cpf_conciliado = false`.
 *
 * O estado "não conciliado" só nasce no backfill de subida (nenhum endpoint o cria), então a LISTA é simulada aqui: o que se
 * prova é a tela — quando o aviso aparece, que exige justificativa e manda a ação certa. Que o backend de fato entrega
 * `cpfConciliado` está em 09-contratos-api; que a ação tira a marca e devolve o prestador à busca, no E2E Java (passo 68).
 */

const DUDA = { id: '7b1f0c1e-3a55-4a1b-9c58-0d1a2b3c4d5e', nome: 'Duda Duplicada', categoria: 'eletrica',
  statusVerificacao: 'VERIFICADO', notaMedia: null, cpfConciliado: false };
const BETO = { id: '0c9d8e7f-6a5b-4c3d-8e2f-1a0b9c8d7e6f', nome: 'Beto Normal', categoria: 'pintura',
  statusVerificacao: 'EM_VERIFICACAO', notaMedia: null, cpfConciliado: true };

async function simularLista(page: Page, itens: object[]) {
  await page.route('**/admin/providers', route => route.fulfill({ json: itens }));
}

test.describe('Conciliar CPF (duplicata legada)', () => {
  test('o aviso exige justificativa e manda CONCILIAR_CPF com ela; depois o aviso some', async ({ page }) => {
    let lista: object[] = [DUDA];
    const posts: unknown[] = [];
    await page.route('**/admin/providers', route => route.fulfill({ json: lista }));
    await page.route('**/admin/providers/*/moderate', route => {
      posts.push(route.request().postDataJSON());
      lista = [{ ...DUDA, cpfConciliado: true }];   // depois da ação o backend devolve o perfil conciliado
      return route.fulfill({ status: 200, body: '' });   // como o backend real: 200 sem corpo
    });
    await loginAdmin(page);
    await page.goto(`/providers/${DUDA.id}`);

    await expect(page.getByText('CPF em duplicidade')).toBeVisible();
    // a justificativa vai para o log de auditoria: o backend recusa mais de 500 caracteres, a tela já não deixa digitar além disso
    await expect(page.getByPlaceholder(/O que foi verificado/)).toHaveAttribute('maxlength', '500');
    const conciliar = page.getByRole('button', { name: 'Conciliar CPF' });
    await expect(conciliar).toBeDisabled();   // decidir uma duplicata às cegas não é uma opção: a justificativa vai para a auditoria
    await page.getByPlaceholder(/O que foi verificado/).fill('   ');
    await expect(conciliar).toBeDisabled();   // só espaços não vale
    await page.getByPlaceholder(/O que foi verificado/).fill('  Conferi o RG e o comprovante das duas contas: esta é a verdadeira.  ');
    await expect(conciliar).toBeEnabled();
    await conciliar.click();

    await expect(page.getByText('CPF em duplicidade')).toHaveCount(0);
    expect(posts).toEqual([{ action: 'CONCILIAR_CPF',
      justificativa: 'Conferi o RG e o comprovante das duas contas: esta é a verdadeira.' }]);
  });

  test('prestador sem duplicidade não mostra o aviso, e a moderação de sempre continua lá', async ({ page }) => {
    await simularLista(page, [{ ...DUDA, cpfConciliado: true }]);
    await loginAdmin(page);
    await page.goto(`/providers/${DUDA.id}`);

    await expect(page.getByText('Duda Duplicada')).toBeVisible();
    await expect(page.getByText('CPF em duplicidade')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Suspender prestador' })).toBeVisible();
  });

  test('backend sem o campo (undefined) não acende o aviso: só o false literal vale', async ({ page }) => {
    const { cpfConciliado: _omitido, ...semOCampo } = DUDA;   // eslint-disable-line @typescript-eslint/no-unused-vars
    await simularLista(page, [semOCampo]);
    await loginAdmin(page);
    await page.goto(`/providers/${DUDA.id}`);

    await expect(page.getByText('Duda Duplicada')).toBeVisible();
    await expect(page.getByText('CPF em duplicidade')).toHaveCount(0);
  });

  test('a lista ganha o filtro "CPF duplicado" (a duplicata costuma estar VERIFICADA) e o selo na linha', async ({ page }) => {
    await simularLista(page, [DUDA, BETO]);
    await loginAdmin(page);
    await page.goto('/providers');

    // aba padrão "Em verificação": a Duda, VERIFICADA, ficaria escondida sem o filtro novo
    await expect(page.getByText('Beto Normal')).toBeVisible();
    await expect(page.getByText('Duda Duplicada')).toHaveCount(0);

    await page.getByText('CPF duplicado · 1').click();
    await expect(page.getByText('Duda Duplicada')).toBeVisible();
    await expect(page.getByText('CPF DUPLICADO', { exact: true })).toBeVisible();
    await expect(page.getByText('Beto Normal')).toHaveCount(0);

    await page.getByText('Ver perfil →').click();
    await expect(page).toHaveURL(`/providers/${DUDA.id}`);
  });

  test('sem nenhuma duplicata o filtro nem aparece: a barra de sempre não muda', async ({ page }) => {
    await simularLista(page, [BETO]);
    await loginAdmin(page);
    await page.goto('/providers');

    await expect(page.getByText(/^Em verificação · \d+$/)).toBeVisible();
    await expect(page.getByText(/^CPF duplicado/)).toHaveCount(0);
  });

  test('duplicata ainda EM VERIFICAÇÃO mantém Verificar/Reprovar e ganha o caminho até o perfil, onde se concilia', async ({ page }) => {
    await simularLista(page, [{ ...BETO, cpfConciliado: false }]);
    await loginAdmin(page);
    await page.goto('/providers');

    await expect(page.getByText('CPF DUPLICADO', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Verificar' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reprovar' })).toBeVisible();
    await page.getByText('Ver perfil →').click();
    await expect(page.getByText('CPF em duplicidade')).toBeVisible();
  });
});
