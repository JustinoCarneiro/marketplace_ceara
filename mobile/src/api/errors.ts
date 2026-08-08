/** Lançado no lugar de `new Error('HTTP error')` — carrega o status pra distinguir a causa. */
export class HttpError extends Error {
  status: number;
  constructor(status: number) {
    super(`HTTP ${status}`);
    this.status = status;
  }
}

export interface ScreenErrorInfo { title: string; body: string }

/**
 * `<ScreenState>` só sabia dizer "vazio" ou "erro" — mesma mensagem genérica de rede pra
 * sessão expirada, permissão negada ou falha real de conexão. Resolve a mensagem certa pra
 * cada causa: `HttpError` = o servidor respondeu (o problema não é a internet do usuário);
 * qualquer outra coisa (fetch rejeitou antes de chegar resposta) = falha de conexão de fato.
 */
export function screenStateError(e: unknown): ScreenErrorInfo {
  if (e instanceof HttpError) {
    if (e.status === 401 || e.status === 403) {
      return { title: 'Sessão expirada', body: 'Faça login novamente para continuar.' };
    }
    if (e.status >= 500) {
      return { title: 'Erro no servidor', body: 'Algo deu errado do nosso lado. Tente novamente em instantes.' };
    }
    return { title: 'Não foi possível carregar', body: 'Algo deu errado ao buscar os dados. Tente novamente.' };
  }
  return { title: 'Sem conexão', body: 'Verifique sua internet e tente novamente.' };
}
