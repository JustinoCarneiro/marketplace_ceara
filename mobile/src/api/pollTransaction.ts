import { API_BASE } from './config';

export interface TransactionPollResult {
  confirmed: boolean;
  prestadorNome: string | null;
  valorTotal: number | null;
}

/**
 * Aguarda a confirmação real do pagamento (transacao.statusPagamento === 'RETIDO'). A
 * confirmação vem do gateway por evento assíncrono (Saga/Outbox, princípio do CLAUDE.md) —
 * nunca é instantânea à chamada do cliente. Faz polling com teto de tentativas em vez de
 * travar a tela esperando indefinidamente; quem chama decide o que fazer se estourar o tempo.
 */
export async function pollPaymentConfirmed(
  requestId: string,
  token: string | null,
  { intervalMs = 1500, maxAttempts = 14 }: { intervalMs?: number; maxAttempts?: number } = {},
): Promise<TransactionPollResult> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const res = await fetch(`${API_BASE}/service-requests/${requestId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok) {
      const data = await res.json();
      if (data?.transacao?.statusPagamento === 'RETIDO') {
        return {
          confirmed: true,
          prestadorNome: data.prestadorNome ?? null,
          valorTotal: data.transacao.valorTotal ?? null,
        };
      }
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  return { confirmed: false, prestadorNome: null, valorTotal: null };
}
