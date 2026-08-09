const ENV_URL = process.env.EXPO_PUBLIC_API_URL;

// Emulador Android → 10.0.2.2 aponta para localhost do host
// Dispositivo/Appetize → defina EXPO_PUBLIC_API_URL com a URL pública (ngrok, etc.)
export const API_BASE = ENV_URL ?? 'http://10.0.2.2:8080/api/v1';

/**
 * Chave de idempotência para endpoints que criam pedido ou movem dinheiro.
 *
 * <p>Só timestamp NÃO serve: `Date.now()` colide entre usuários diferentes no mesmo
 * milissegundo, e a chave é o que decide se a requisição é "a mesma" — colisão fazia um
 * cliente receber o pedido de outro. O sufixo aleatório torna a colisão desprezível
 * (o backend ainda escopa por dono, em V14, como segunda camada).
 */
export function newIdempotencyKey(prefixo: string) {
  return `${prefixo}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
