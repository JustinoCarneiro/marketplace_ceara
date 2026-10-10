import { API_BASE } from './config';

/** Resultado da exclusão: ok, ou a mensagem pronta para mostrar. */
export type ResultadoExclusao = { ok: true } | { ok: false; mensagem: string };

const SEM_CONEXAO = 'Sem conexão. Verifique sua internet e tente de novo.';
const SESSAO_EXPIRADA = 'Sua sessão expirou. Entre de novo para excluir a conta.';

/**
 * Exclui a conta de quem está logado (US36). 204 = excluída: quem chama encerra a sessão local.
 * Os erros de negócio — senha incorreta, pedido em andamento, pagamento a receber — chegam como 422
 * com a mensagem pronta para o usuário (ela diz o que resolver); 401 é sessão expirada, sem corpo.
 */
export async function excluirConta(accessToken: string, senha: string): Promise<ResultadoExclusao> {
  try {
    const res = await fetch(`${API_BASE}/users/me/delete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify({ senha }),
    });
    if (res.status === 204) return { ok: true };
    if (res.status === 401) return { ok: false, mensagem: SESSAO_EXPIRADA };
    const data = await res.json().catch(() => ({}));
    return { ok: false, mensagem: data.message ?? 'Não foi possível excluir a conta. Tente novamente.' };
  } catch {
    return { ok: false, mensagem: SEM_CONEXAO };
  }
}
