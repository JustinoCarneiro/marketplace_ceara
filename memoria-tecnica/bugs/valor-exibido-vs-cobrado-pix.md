---
tipo: bug
data: 2026-09-29
severidade: Alta
status: Resolvido — comissão de 10% descontada do prestador; tela do cliente mostra só o valor da proposta
resolvido_em: 2026-09-29
---

# Tela de pagamento mostra o valor com comissão; o Pix cobra só o valor da proposta

## Sintoma
`PaymentChoiceScreen` calcula `total = valor + 10%` ("Comissão Onda (10%)") e
`PaymentPixScreen` exibe esse total acima do QR. O backend cobra
`transaction.valorTotal`, que é o valor da proposta, sem acréscimo
(`PaymentService.criar` → `MercadoPagoGatewayService.cobrar` usa `getValorTotal()`).
Exemplo: proposta de R$ 150 → a tela mostra R$ 165,00 e o botão "Pagar R$ 165,00",
mas o QR/copia-e-cola cobra R$ 150,00. Enquanto o QR era mockado ninguém via a
divergência; ficou visível com o QR real (2026-09-29).

## Causa raiz
Duas regras de comissão que nunca foram reconciliadas:
- **Mobile:** 10% **somados por cima** do valor (`COMISSAO = 0.1` em
  `PaymentChoiceScreen.tsx`; `valor` vem de `p.valor` em `CompareProposalsScreen`).
- **Backend:** `marketplace.comissao` com default **15%** (`application.yml:74`),
  aplicada como **desconto no repasse ao prestador** (`valor_comissao = valor_total ×
  pct`; repasse = total − comissão), como a spec descreve (US17: "comissão 10–25% para
  a plataforma, restante ao prestador").

Nenhum teste compara o valor exibido com o cobrado.

## Solução
Decisão do Marcos (2026-09-29): **comissão de 10%, descontada do prestador** (opção "a"). O cliente
paga o valor da proposta — como a spec, os Termos ("a comissão é descontada no repasse") e a tela do
prestador ("Você recebe após comissão") já diziam; a tela do cliente era a única que discordava.

- `PaymentChoiceScreen`: removidos a linha e o cálculo de comissão; "Total" e "Pagar R$" mostram o
  valor da proposta, o mesmo que segue para `PaymentPixScreen`/`PaymentCardScreen` e que o Pix cobra.
- Backend: percentual padrão 15% → **10%** (`application.yml`, fallback em `PaymentService`,
  `.env.example`). `MARKETPLACE_COMISSAO` definida no ambiente continua tendo precedência —
  conferir se o Coolify define a variável antes de contar com o novo padrão.
- Testes que faltavam: `PaymentServiceTest` (cobra a proposta; comissão de 20 sobre 200),
  `MercadoPagoGatewayServiceTest` (`transaction_amount` = 250; antes só `.exists()`) e
  `mobile/tests/02-fluxo-pedido-completo.spec.ts` (botão "Pagar R$ 150,00", nenhuma "Comissão" na
  tela do cliente, valor acima do QR = R$ 150,00). Provado que o teste reprova a tela antiga
  (falha em `getByText('Pagar R$ 150,00')`) e passa com a corrigida (7/7).

**Risco residual:** `SendProposalScreen.tsx` ainda tem `COMISSAO = 0.1` fixo no código. Hoje bate com
o backend; se o percentual mudar lá, o prestador volta a ver um valor líquido errado. Correção
estrutural: o backend expor o percentual e a tela do prestador ler dele.

**Achado de teste (não corrigido):** em `02-fluxo-pedido-completo.spec.ts` o `ts = Date.now()` fica no
topo do arquivo; quando um teste falha o Playwright reinicia o worker e reavalia o arquivo, então o
retry usa e-mails que nunca foram cadastrados (login 422) — os retries desse arquivo só atrapalham.

## Ligado a
- [[mercadopago-escrow-modelo-de-repasse]]
