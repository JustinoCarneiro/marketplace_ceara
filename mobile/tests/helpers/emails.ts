/// <reference types="node" />
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Pasta onde o backend de dev/CI grava os e-mails ao usuário (NOTIFICATION_MAIL_SINK_DIR). O código
 * de recuperação de senha só existe no e-mail — não há endpoint nem log que o exponha —, então o
 * teste o lê daqui, como o usuário leria na caixa de entrada.
 */
const PASTA_EMAILS = process.env.MAIL_SINK_DIR ?? '/tmp/onda-emails';

function arquivosPara(email: string): { caminho: string; quando: number }[] {
  try {
    return readdirSync(PASTA_EMAILS)
      .filter(f => f.endsWith(`-${email}.txt`))
      .map(f => ({ caminho: join(PASTA_EMAILS, f), quando: statSync(join(PASTA_EMAILS, f)).mtimeMs }))
      .sort((a, b) => b.quando - a.quando);
  } catch {
    return []; // a pasta só existe depois do primeiro e-mail
  }
}

/** Há algum e-mail gravado para este endereço? (e-mail desconhecido nunca recebe nada) */
export function recebeuEmail(email: string): boolean {
  return arquivosPara(email).length > 0;
}

/**
 * Espera chegar o e-mail com o código ao endereço e devolve o código no formato XXXX-XXXX. Só
 * considera e-mails gravados depois de `aposMs` — o código do pedido anterior já foi invalidado.
 * O aviso de "senha alterada" não tem código e é ignorado.
 */
export async function lerCodigoRecebido(email: string, aposMs: number, timeoutMs = 15000): Promise<string> {
  const limite = Date.now() + timeoutMs;
  while (Date.now() < limite) {
    for (const { caminho, quando } of arquivosPara(email)) {
      if (quando < aposMs) continue;
      const m = readFileSync(caminho, 'utf8').match(/\b([0-9A-Z]{4})-([0-9A-Z]{4})\b/);
      if (m) return `${m[1]}-${m[2]}`;
    }
    await new Promise(r => setTimeout(r, 250));
  }
  throw new Error(`Nenhum e-mail com código chegou para ${email} em ${PASTA_EMAILS} — o backend foi iniciado com NOTIFICATION_MAIL_SINK_DIR=${PASTA_EMAILS}?`);
}
