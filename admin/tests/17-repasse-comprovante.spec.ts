import { test, expect } from '@playwright/test';

for (const referencia of ['E2E-12345', '  E2E-12345  ']) {
  test(`confirmação de repasse exige referência e normaliza ${JSON.stringify(referencia)}`, async ({ page }) => {
    const txId = '99999999-9999-4999-8999-999999999999';
    let enviado: unknown;
    const rotasSemSimulacao: string[] = [];
    // Toda a API deste teste é simulada; não depende de login/backend em execução.
    await page.addInitScript(() => sessionStorage.setItem('onda_admin_token', 'sessao-simulada-de-teste'));
    // Uma rota esquecida deve falhar também sem backend local, como ocorreria no CI.
    await page.route('**/api/v1/**', route => {
      rotasSemSimulacao.push(route.request().url());
      return route.fulfill({ status: 401, json: { message: 'Rota sem simulação no teste' } });
    });
    for (const recurso of ['disputes', 'notifications']) {
      await page.route(`**/api/v1/admin/${recurso}`, route => route.fulfill({ json: [] }));
    }
    await page.route('**/api/v1/admin/transactions?status=*', route =>
      route.fulfill({ json: [] }));
    await page.route('**/api/v1/admin/outbox?status=PROCESSANDO', route => route.fulfill({ json: [] }));
    await page.route('**/api/v1/admin/outbox?status=FALHA', route =>
      route.fulfill({ json: [{ id: '88888888-8888-4888-8888-888888888888', agregado: 'transaction',
        agregadoId: txId, tipoEvento: 'PAYMENT_RELEASED', tentativas: 1, status: 'FALHA' }] }));
    await page.route(`**/api/v1/admin/transactions/${txId}/repasse-pendente`, route =>
      route.fulfill({ json: { transactionId: txId, prestadorNome: 'Prestador Teste',
        valorARepassar: 100, chavePixCadastrada: true, chavePix: 'teste@pix.com' } }));
    await page.route(`**/api/v1/admin/transactions/${txId}/confirmar-repasse-manual`, async route => {
      enviado = route.request().postDataJSON();
      await route.fulfill({ status: 200 });
    });

    await page.goto('/finance');
    await page.getByRole('button', { name: 'Repasse pendente' }).click();
    const confirmar = page.getByRole('button', { name: 'Já paguei — confirmar' });
    await expect(confirmar).toBeDisabled();
    const campo = page.getByLabel('Referência do comprovante');
    await campo.fill('   ');
    await expect(confirmar).toBeDisabled();
    await campo.fill('REF@INVALIDA');
    await expect(confirmar).toBeDisabled();
    await campo.fill(referencia);
    await expect(confirmar).toBeEnabled();
    const resposta = page.waitForResponse(response => response.url().endsWith('/confirmar-repasse-manual'));
    await confirmar.click();
    await resposta;
    expect(enviado).toEqual({ comprovanteReferencia: 'E2E-12345' });
    expect(rotasSemSimulacao).toEqual([]);
  });
}

test('operação financeira em processamento aparece com início e bloqueia repasse concorrente', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('onda_admin_token', 'sessao-simulada-de-teste'));
  await page.route('**/api/v1/**', route => route.fulfill({ json: [] }));
  const active = { id: 'active-event', agregado: 'transaction', agregadoId: 'same-payment',
    tipoEvento: 'PAYMENT_INITIATED', tentativas: 0, status: 'PROCESSANDO', processamentoEm: '2026-10-17T12:00:00Z' };
  await page.route('**/api/v1/admin/outbox?status=PROCESSANDO', route => route.fulfill({ json: [active] }));
  await page.route('**/api/v1/admin/outbox?status=FALHA', route => route.fulfill({ json: [
    { ...active, id: 'failed-event', tipoEvento: 'PAYMENT_RELEASED', status: 'FALHA' },
  ] }));
  await page.goto('/finance');
  await expect(page.getByText('Operações em processamento: 1')).toBeVisible();
  await expect(page.getByText(/PAYMENT_INITIATED · Início:/)).toBeVisible();
  await expect(page.getByText('Operação em processamento', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Repasse pendente' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Reprocessar', exact: true })).toHaveCount(0);
});
