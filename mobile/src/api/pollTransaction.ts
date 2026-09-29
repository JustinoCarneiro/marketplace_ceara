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

export interface PixDados {
  qrCode: string;
  qrCodeBase64: string | null;
  ticketUrl: string | null;
}

/**
 * Aguarda o QR/copia-e-cola do Pix (transaction.pixQrCode). Também não é instantâneo:
 * o gateway é chamado pelo OutboxProcessor, fora da resposta HTTP que criou a
 * transação (mesmo princípio Escrow/Saga do CLAUDE.md que motiva pollPaymentConfirmed).
 * Em profile=seed (demo/CI, gateway stub) nunca chega — quem chama trata o `null`
 * como "QR indisponível" e mantém o botão "Paguei" disponível mesmo assim.
 */
export async function pollPixDados(
  requestId: string,
  token: string | null,
  { intervalMs = 1500, maxAttempts = 8 }: { intervalMs?: number; maxAttempts?: number } = {},
): Promise<PixDados | null> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const res = await fetch(`${API_BASE}/transactions/${requestId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok) {
      const data = await res.json();
      if (data?.pixQrCode) {
        return {
          qrCode: data.pixQrCode,
          qrCodeBase64: data.pixQrCodeBase64 ?? null,
          ticketUrl: data.pixTicketUrl ?? null,
        };
      }
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  return null;
}
