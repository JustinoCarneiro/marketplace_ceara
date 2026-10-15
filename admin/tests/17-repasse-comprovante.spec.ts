import { test, expect } from '@playwright/test';
import { loginAdmin } from './helpers/auth';

test('confirmação de repasse exige referência e a envia ao backend', async ({ page }) => {
  const txId = '99999999-9999-4999-8999-999999999999';
  let enviado: unknown;
  await loginAdmin(page);
  await page.route('**/api/v1/admin/transactions?status=*', route =>
    route.fulfill({ json: [] }));
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
  await page.getByLabel('Referência do comprovante').fill('E2E-12345');
  await confirmar.click();
  expect(enviado).toEqual({ comprovanteReferencia: 'E2E-12345' });
});
