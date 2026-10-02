import { API_BASE } from './config';

/** Resultado de uma chamada da recuperação de senha: ok, ou a mensagem pronta para mostrar. */
export type Resultado = { ok: true } | { ok: false; mensagem: string };

const SEM_CONEXAO = 'Sem conexão. Verifique sua internet e tente de novo.';

/** E-mail com cara de e-mail. Quem decide se a conta existe é o backend — e ele nunca conta. */
export function emailValido(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());
}

/**
 * Mostra o código como ele vem no e-mail: MAIÚSCULAS, só letras e dígitos, 8 posições, com hífen
 * depois da 4ª (ABCD-2345). O backend aceita o código de qualquer jeito digitado; isto só ajuda
 * a conferir o que foi digitado.
 */
export function formatarCodigo(digitado: string): string {
  const limpo = digitado.toUpperCase().replace(/[^0-9A-Z]/g, '').slice(0, 8);
  return limpo.length > 4 ? `${limpo.slice(0, 4)}-${limpo.slice(4)}` : limpo;
}

/** Os 8 caracteres do código foram digitados (o hífen de leitura não conta). */
export function codigoCompleto(codigo: string): boolean {
  return codigo.replace(/-/g, '').length === 8;
}

async function postar(caminho: string, corpo: object, statusOk: number, mensagemPadrao: string): Promise<Resultado> {
  try {
    const res = await fetch(`${API_BASE}${caminho}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo),
    });
    if (res.status === statusOk) return { ok: true };
    // 204 não tem corpo, mas os erros (422) têm: a mensagem do backend é a que o usuário lê.
    const data = await res.json().catch(() => ({}));
    return { ok: false, mensagem: data.message ?? mensagemPadrao };
  } catch {
    return { ok: false, mensagem: SEM_CONEXAO };
  }
}

/**
 * Pede o código de recuperação (US35). A resposta é a mesma para e-mail cadastrado e
 * desconhecido — por isso o app avança do mesmo jeito nos dois casos. Só falha de verdade se o
 * servidor não tem e-mail configurado ("indisponível") ou se não há rede.
 */
export function pedirCodigo(email: string): Promise<Resultado> {
  return postar('/auth/forgot-password', { email: email.trim() }, 202,
    'Não foi possível pedir o código. Tente novamente.');
}

/** Troca a senha com o código recebido (US35). 204 = trocada e todas as sessões encerradas. */
export function redefinirSenha(email: string, codigo: string, novaSenha: string): Promise<Resultado> {
  return postar('/auth/reset-password', { email: email.trim(), codigo, novaSenha }, 204,
    'Não foi possível redefinir a senha. Tente novamente.');
}
