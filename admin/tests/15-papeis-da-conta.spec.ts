import { test, expect, request } from '@playwright/test';
import { loginAdmin } from './helpers/auth';
import { cpfNovo } from './helpers/cpf';

/**
 * Conta única com papéis: a mesma pessoa é cliente e prestador na MESMA conta. O painel mostra TODOS os papéis da conta —
 * antes, qualquer conta que não fosse `ROLE_PROVIDER` aparecia como "Cliente" (inclusive o admin) e o prestador não
 * mostrava que também contrata. As contas são criadas pela API, então a tela mostra o que o backend devolve.
 */

const API = process.env.API_BASE_URL ?? 'http://localhost:8080/api/v1';

async function cadastrar(tipo: 'client' | 'provider') {
  const ctx = await request.newContext();
  const ts = Date.now();
  const corpo = tipo === 'client'
    ? { nome: 'Cliente Papel', email: `papel.cliente.${ts}@teste.com`, senha: 'Senha@123', aceitouTermos: true }
    : { nome: 'Prestador Papel', email: `papel.prestador.${ts}@teste.com`, senha: 'Senha@123', cpf: cpfNovo(),
        categoria: 'Elétrica', aceitouTermos: true };
  const res = await ctx.post(`${API}/auth/register/${tipo}`, { data: corpo });
  expect(res.status(), `cadastro (${tipo})`).toBe(201);
  const dados = await res.json();
  return { userId: dados.userId as string, token: dados.accessToken as string, refresh: dados.refreshToken as string, papeis: dados.papeis as string[] };
}

test.describe('Papéis da conta no painel', () => {
  test('o prestador aparece como "Cliente + Prestador" (todo prestador também contrata); o cliente, só "Cliente"', async ({ page }) => {
    const prestador = await cadastrar('provider');
    const cliente = await cadastrar('client');
    expect(prestador.papeis).toEqual(['ROLE_CLIENT', 'ROLE_PROVIDER']);

    await loginAdmin(page);
    await page.goto('/users');

    await expect(page.getByTestId(`papeis-${prestador.userId}`)).toHaveText('Cliente + Prestador');
    await expect(page.getByTestId(`papeis-${cliente.userId}`)).toHaveText('Cliente');
  });

  test('quem vira prestador pela própria conta passa a aparecer com os dois papéis, na mesma linha', async ({ page }) => {
    const cliente = await cadastrar('client');
    const ctx = await request.newContext();
    const res = await ctx.post(`${API}/auth/become-provider`, {
      headers: { Authorization: `Bearer ${cliente.token}` },
      data: { cpf: cpfNovo(), categoria: 'Elétrica', bio: 'Instalações', aceitouTermos: true },
    });
    expect(res.status(), 'become-provider').toBe(201);

    await loginAdmin(page);
    await page.goto('/users');

    // a MESMA linha (mesmo id) muda de "Cliente" para "Cliente + Prestador": não nasce uma segunda conta
    await expect(page.getByTestId(`papeis-${cliente.userId}`)).toHaveText('Cliente + Prestador');
    await expect(page.getByTestId(`usuario-${cliente.userId}`)).toHaveCount(1);
  });

  test('o contrato da API: cada usuário traz `papeis` (lista) além de `role`', async () => {
    const prestador = await cadastrar('provider');
    const ctx = await request.newContext();
    const login = await ctx.post(`${API}/auth/login`, { data: { email: 'admin@onda.com', senha: 'admin123' } });
    const { accessToken } = await login.json();
    const lista = await (await ctx.get(`${API}/admin/users`, { headers: { Authorization: `Bearer ${accessToken}` } })).json();

    const u = lista.find((x: { id: string }) => x.id === prestador.userId);
    expect(u.role).toBe('ROLE_PROVIDER');
    expect(u.papeis).toEqual(['ROLE_CLIENT', 'ROLE_PROVIDER']);
  });
});
