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
  **conferido na VPS em 2026-09-29:** não está definida no container nem nos arquivos de deploy do
  Coolify, então o padrão de 10% vale a partir do próximo deploy. O `docker-compose.prod.yml` também
  não repassa essa variável ao container — pra sobrescrever, é preciso listá-la em `environment:`.
  A demo pública foi **republicada em 2026-09-29** (Coolify, master `7c11882`; migrations V18 e V19
  aplicadas sem perder dados) e já roda a tela nova e o padrão de 10%.
- Testes que faltavam: `PaymentServiceTest` (cobra a proposta; comissão de 20 sobre 200),
  `MercadoPagoGatewayServiceTest` (`transaction_amount` = 250; antes só `.exists()`) e
  `mobile/tests/02-fluxo-pedido-completo.spec.ts` (botão "Pagar R$ 150,00", nenhuma "Comissão" na
  tela do cliente, valor acima do QR = R$ 150,00). Provado que o teste reprova a tela antiga
  (falha em `getByText('Pagar R$ 150,00')`) e passa com a corrigida (7/7).

**Risco residual — resolvido em 2026-09-29.** `SendProposalScreen.tsx` tinha `COMISSAO = 0.1` fixo:
batia com o backend só enquanto ninguém mudasse `MARKETPLACE_COMISSAO`. Agora o backend expõe o
percentual (`GET /api/v1/payments/comissao`, mesma configuração da cobrança) e a tela do prestador lê
dele; sem resposta, a linha "Você recebe" some em vez de chutar um número (enviar a proposta não
depende dela). O valor é calculado em centavos inteiros com o meio arredondado pra cima
(`valorAposComissao`), que é o que o `NUMERIC(12,2)` do banco faz — em ponto flutuante, R$ 10,05 a 10%
mostrava 9,05 e o repasse real é 9,04. Testes: `PaymentControllerTest` (endpoint), `E2EFluxoPrincipalTest`
(o percentual do endpoint é o mesmo que a cobrança aplicou, com token de prestador; sem token, 401),
`mobile/tests/07-comissao-calculo.spec.ts` (cálculo) e o passo do prestador em
`02-fluxo-pedido-completo.spec.ts` (deixa passar a resposta REAL do backend e troca o número por 25%:
provado que reprova a tela antiga — recebia "R$ 135,00" em vez de "R$ 112,50" — e passa com a nova).

**Achado de teste — resolvido em 2026-09-29.** Em `02-fluxo-pedido-completo.spec.ts` o `ts = Date.now()`
fica no topo do arquivo; quando um teste falha o Playwright reinicia o worker e reavalia o arquivo, então o
retry usava e-mails que nunca foram cadastrados (login 422). Provado com um probe descartável: no retry,
`ts` mudava (`…464` → `…794`). Correção: `test.describe.configure({ mode: 'serial' })` — no retry o
Playwright refaz o grupo inteiro (o `setup` roda de novo com o mesmo `ts` novo) e os testes seguintes são
pulados em vez de falhar por consequência. Aplicado também em 03, 05 e 06 (mesma estrutura); 01 e 04 não
dependem de ordem.

**Outro tropeço visto na mesma rodada — também resolvido em 2026-09-29:** no **backend frio**, o teste
"cliente cria o pedido e publica" falhava em "Pedido criado!" (e "com o backend quente passava"). Em
`AiAssistantScreen` o botão fica `disabled={publishing || loading}` enquanto a IA analisa, e a primeira
chamada de `ai-suggestion` de um backend recém-subido demora; o teste só esperava o texto do botão
(visível mesmo desabilitado) e o clique era engolido — e como `Alert.alert` não faz nada no
react-native-web, nada aparecia. Provado de forma determinística com um spec descartável que atrasa a
resposta da IA em 4 s: a sequência antiga falha, a nova passa. Correção: os specs 02, 03, 05 e 06 passam a
esperar o formulário ("Descrição sugerida", que só existe depois da análise) antes de clicar.

## Ligado a
- [[mercadopago-escrow-modelo-de-repasse]]
