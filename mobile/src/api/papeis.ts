import { API_BASE } from './config';
import type { Sessao } from '../store/auth';

/** Resultado de uma ação que devolve uma nova sessão: a sessão, ou a mensagem pronta para mostrar. */
export type ResultadoSessao = { ok: true; sessao: Sessao } | { ok: false; mensagem: string };

const SEM_CONEXAO = 'Sem conexão. Verifique sua internet e tente de novo.';
const SESSAO_EXPIRADA = 'Sua sessão expirou. Entre de novo para continuar.';

async function pedirSessao(caminho: string, accessToken: string, corpo: object, falha: string): Promise<ResultadoSessao> {
  try {
    const res = await fetch(`${API_BASE}${caminho}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
      body: JSON.stringify(corpo),
    });
    if (res.status === 401) return { ok: false, mensagem: SESSAO_EXPIRADA };
    const data = await res.json().catch(() => ({}));
    if (!res.ok) return { ok: false, mensagem: data.message ?? falha };
    return { ok: true, sessao: data as Sessao };
  } catch {
    return { ok: false, mensagem: SEM_CONEXAO };
  }
}

/**
 * Conta única com papéis: alterna a MESMA conta entre cliente e prestador, sem novo login. O servidor emite um token no
 * novo papel e revoga a sessão anterior (por isso o refresh token vai junto — e é obrigatório: o servidor recusa a
 * troca sem ele, achado da revisão cruzada de 2026-10-05, para um access token sozinho não bastar).
 */
export function alternarPapel(accessToken: string, refreshToken: string, papel: 'ROLE_CLIENT' | 'ROLE_PROVIDER') {
  return pedirSessao('/auth/switch-role', accessToken, { papel, refreshToken },
    'Não foi possível trocar de modo. Tente novamente.');
}

export interface DadosPrestador { cpf: string; categoria: string; bio: string; aceitouTermos: boolean; refreshToken: string }

/** "Quero ser prestador": o cliente logado passa a prestar serviço NA MESMA conta (fica em verificação). */
export function tornarPrestador(accessToken: string, dados: DadosPrestador) {
  return pedirSessao('/auth/become-provider', accessToken, dados,
    'Não foi possível enviar seu cadastro de prestador. Tente novamente.');
}
