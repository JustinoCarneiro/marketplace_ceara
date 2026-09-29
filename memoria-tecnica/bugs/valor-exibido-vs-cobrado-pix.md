---
tipo: bug
data: 2026-09-29
severidade: Alta
status: Aberto — aguarda decisão de produto (comissão por dentro ou por fora)
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
Pendente — decisão de produto:
- **(a)** comissão descontada do prestador (spec atual; o cliente paga o valor da
  proposta) → tirar a linha "Comissão" da UI de pagamento do cliente e mostrar só o
  valor da proposta; ou
- **(b)** comissão somada ao cliente (UI atual) → o backend precisa cobrar
  `proposta + comissão` e o repasse muda.

Em qualquer caso, alinhar o percentual (10% no app × 15% no backend) e cobrir com
teste que compare o valor exibido com o cobrado.

## Ligado a
- [[mercadopago-escrow-modelo-de-repasse]]
