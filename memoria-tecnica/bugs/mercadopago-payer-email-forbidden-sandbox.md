---
tipo: bug
data: 2026-09-28
severidade: Média
status: Resolvido — 403 era e-mail @testuser.com; ciclo real validado em 2026-09-28
resolvido_em: 2026-09-28
---

# `POST /v1/payments` (Pix) recusa qualquer comprador de teste com 403 "Payer email forbidden"

## Sintoma
Testando a cobrança Pix real (retomada do MKT-49, ver
[[mercadopago-escrow-modelo-de-repasse]]) com a credencial de **produção**
(obtida da seção **Produção** do painel da app "Onda Marketplace", de
Checkout Transparente/API Pagamentos),
toda chamada a `POST /v1/payments` com `payer.email` de uma conta de teste
devolve `403`:

```json
{
  "cause": [{"code": 4390, "description": "Payer email forbidden", "data": "<ts>;<request-id>"}],
  "error": "forbidden", "message": "Payer email forbidden", "status": 403
}
```

Reproduzido 3x, com 2 compradores de teste diferentes (um já existente no
painel, um criado na hora via `POST /users/test_user` — e-mail confirmado
válido na própria resposta da API, não adivinhado):
- `29-09-2026T00:33:31UTC;4eaed1fc-549f-4c5f-97be-bf43cbc15ecf`
- `29-09-2026T00:34:24UTC;4b9a0ce8-6ee6-4833-a6da-a4bc337d6199`
- `29-09-2026T00:44:11UTC;072dfc46-3a03-4b75-952d-5e238e444bd8`

## Já descartado com evidência (não sugerir de novo sem novidade)
- **E-mail do comprador errado/mal formatado** — descartado: 2º e 3º teste
  usaram e-mail devolvido literalmente por `POST /users/test_user`, não
  derivado do nickname do painel.
- **Credencial errada** — descartado: veio da seção **Produção** do painel
  da aplicação, não apenas inferida do prefixo `APP_USR-`. A investigação
  anterior observou `TEST-` sem sucesso nesse fluxo (ver
  `onda-starter/memoria-tecnica/bugs/mercadopago-sandbox-exige-credencial-de-producao.md`).
  A [documentação oficial de compra teste via Payments API](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/integration-test/make-test-purchase)
  ainda recomenda credenciais de teste; há divergência entre a documentação e
  o observado, mas não há motivo para repetir essa tentativa sem novo dado.
- **Pix desabilitado pra essa conta** — descartado: `GET /v1/payment_methods`
  lista `pix` normalmente.
- **Endereço fiscal pendente** — descartado: `/users/me` mostrava
  `billing.allow=false, codes=["address_pending"]`, mas o perfil
  (`accounts/profile-data`) já tinha "Endereço fiscal" preenchido; sem
  mudança no erro após confirmar isso.
- **Payload/endpoint errado** — descartado: payload é exatamente o que
  `MercadoPagoGatewayService.cobrar()` monta (+ `payer.first_name: "APRO"`,
  só pro teste).
- **"Copiar o que deu certo no confidencial-calcados"** — não aplicável:
  conferido o código real (`confidencial-calcados/lib/mercado-pago.ts:16`),
  aquele projeto usa `POST /checkout/preferences` (Checkout Pro, redireciona
  o comprador pro site do MP, que autentica o usuário de teste ou recebe
  como convidado) — nunca passa `payer.email` direto num `POST /v1/payments`
  de criação. É um caminho de API estruturalmente diferente do nosso
  (Checkout Transparente), não uma configuração que falta copiar aqui.

## Causa raiz (hipótese anterior, não confirmada)
Motor antifraude do Mercado Pago bloqueando a **primeira transação** de uma
conta nova (`seller_experience: "NEWBIE"`, cadastrada em 2026-05, zero
transações históricas em `/users/me`), possivelmente por reconhecer que o
comprador de teste foi criado pela mesma credencial/aplicação que está
cobrando (padrão de auto-transação, mesma família de restrição documentada em
`confidencial-calcados/memoria-tecnica/decisoes/validacao-producao-mercado-pago.md`
— lá era bloqueio de colaborador pagando na própria conta administrada; aqui
é um mecanismo automático, sem login envolvido). A cobrança Pix criada com a
mesma aplicação em 2026-09-29 (abaixo) enfraquece a hipótese de bloqueio geral
da primeira transação da conta. Não há evidência para atribuir o 4390 à idade
da conta vendedora.

## Investigação complementar (2026-09-28) — restrição de e-mail na Payments API

A [documentação oficial brasileira da integração via Métodos Core](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/integration-configuration/card/integrate-via-core-methods),
na seção **Enviar pagamento**, afirma explicitamente que, em ambiente de teste,
`POST /v1/payments` rejeita `payer.email` no formato
`<nickname>@testuser.com` com HTTP 403 `Payer email forbidden` e orienta usar
outro e-mail no campo. A [mesma página em inglês no domínio oficial do Peru](https://www.mercadopago.com.pe/developers/en/docs/checkout-api-payments/integration-configuration/card/integrate-via-core-methods)
também identifica **code 4390**. Essa descrição coincide com o endpoint, o
campo, o status e o código das três respostas observadas aqui. É um indício
concreto de política do MP para o e-mail de conta de teste, mais específico que
a hipótese de conta vendedora nova ou antifraude genérico.

**Limite da evidência:** essa orientação aparece numa página de **cartão**, não
num roteiro de Pix da Payments API. A [documentação oficial de Pix via Payments API](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/integration-configuration/integrate-pix)
e a [documentação do Payment Brick para Pix](https://www.mercadopago.com.br/developers/pt/docs/checkout-bricks/payment-brick/payment-submission/pix)
usam `/v1/payments` com placeholder de e-mail e não explicam o 4390. O
[roteiro de Pix pela Orders API](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/pix)
usa `test_user_br@testuser.com`, mas chama `/v1/orders`: não é prova para
este fluxo. A diferença de endpoint reforça que as orientações de teste não
devem ser transplantadas de Orders para Payments.

A página de Pix via Payments API descreve o envio de `payer.identification`
(tipo e número do documento), ausente em `cobrar()`. Porém o exemplo PHP da
**mesma página** envia só `payer.email`; não há evidência de que a omissão
produza especificamente 403/4390. `X-Idempotency-Key` já está no código e no
repro; não foi encontrado outro header obrigatório para esse erro.

**Validação direcionada executada uma vez, com autorização do Marcos:** em
2026-09-29T01:05:47Z, `POST /v1/payments` usou a credencial da seção
**Produção** da mesma aplicação, `payment_method_id: "pix"`, `payer.email`
como alias controlado no domínio do projeto (não conta de teste MP),
`payer.first_name: "APRO"`, valor de R$ 1,00, vencimento em 35 minutos,
referência e chave de idempotência novas. `notification_url` foi omitida;
nenhuma configuração de webhook foi alterada. **Resposta: HTTP 201, payment ID
`180381125045`, status `pending`, `live_mode: true`, x-request-id
`ef005781-1374-4249-bbea-fba31d28a07a`.** Nenhum Pix foi pago, e não se
fez outro POST nem se criou conta de teste. Token e e-mail não foram impressos
em saída nem incluídos neste arquivo.

O sucesso comprova que a aplicação/conta consegue **criar** um Pix real nesse
endpoint. Combinado com a documentação oficial que associa exatamente 403/4390
ao e-mail `@testuser.com` em `/v1/payments`, é forte evidência de que os três
403 anteriores são restrição ao **e-mail de conta de teste nesse fluxo**,
e não bloqueio geral por `seller_experience: NEWBIE`, Pix desabilitado ou
header obrigatório ausente. Como o ensaio também teve valor, vencimento e
referência próprios, ele não isola matematicamente só o e-mail como variável;
a regra exata para todos os cenários de Pix ainda depende de confirmação MP.
O pagamento `live_mode: true` **não valida um sandbox fictício** e deve apenas
expirar sem pagamento.

**Consulta somente leitura (2026-09-29, após a criação):**
`GET /v1/payments/180381125045` retornou HTTP 200, `status: pending`,
`live_mode: true` e vencimento `2026-09-29T01:40:47Z`. Não houve novo POST.
Na pesquisa complementar, a [página oficial de compra teste da Payments API](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-payments/integration-test/make-test-purchase)
descreve teste com **cartões**; o [roteiro oficial específico de Pix fictício](https://www.mercadopago.com.br/developers/pt/docs/checkout-api-orders/integration-test/pix)
usa **`/v1/orders`**. Este último documenta uma simulação que aprova o
pagamento automaticamente, mas é outra API, com contrato de resposta distinto.

### Limite oficial do Pix via Payments API

O [exemplo Java oficial do Mercado Pago para Pix no Checkout Transparente](https://github.com/mercadopago/pix-payment-sample-java/blob/main/README.pt.md)
afirma que, em testes com usuários de teste, a criação de um Pix pode devolver
o QR Code e deixá-lo **pendente**, porém **não é possível usar o código/QR para
encerrar o fluxo e aprovar o pagamento**. O [exemplo Node oficial](https://github.com/mercadopago/pix-payment-sample-node/blob/main/README.pt.md)
repete a mesma limitação. Os exemplos também orientam cadastrar uma chave Pix
na conta de vendedor de teste; isso é uma possível condição para criar um Pix
fictício **pendente**, mas não soluciona o 403 observado na combinação atual
nem viabiliza a aprovação. Na investigação anterior do `onda-starter`, a
conta de vendedor de teste existente não listava Pix em
`GET /v1/payment_methods` e seu token `APP_USR-` recebeu 401 nesse fluxo;
não há motivo para repetir o teste sem configurar/confirmar um cenário novo.

Esses exemplos são de 2021; não demonstram todas as regras internas atuais
da plataforma. Ainda assim, são evidência primária de que não existe um
procedimento documentado para **aprovar** Pix fictício nesse fluxo clássico.
Nenhum campo extra ou header no nosso `POST /v1/payments` substituirá uma
aprovação que o próprio MP não simula. A confirmação da política atual para
esta aplicação específica cabe ao suporte MP.

## Achados da retomada (2026-09-28, Claude, conferindo o trabalho do Codex)

- **Cobrança `180381125045` conferida** (`GET`, só leitura): real, `live_mode:
  true`, `pending_waiting_transfer`, expirou sem pagamento. O formato da resposta
  Pix agora é conhecido de verdade — `point_of_interaction.transaction_data`
  traz `qr_code` (copia-e-cola, BR Code `000201...br.gov.bcb.pix`),
  `qr_code_base64` (PNG) e `ticket_url`. Isso destrava a etapa 3 do ADR (tela de
  QR/copia-e-cola) sem adivinhar contrato.
- **Risco ainda não validado — assinatura do webhook:** os testes de
  `MercadoPagoWebhookVerifierTest` são 100% sintéticos (assinam com o nosso
  algoritmo e verificam com o mesmo algoritmo), então nunca pegariam uma
  divergência com o que o MP assina de verdade — exatamente o modo de falha do
  SAW HUB (ver memória `saw-hub-mp-webhook-signature-bug`, bug nunca resolvido
  do lado cliente). Validar exige **uma notificação real**. Não precisa de
  pagamento: criar uma cobrança Pix (mesmo não paga) dispara `payment.created`
  assinado. Pré-requisitos: webhook configurado no painel (gera a assinatura
  secreta) apontando pra um endpoint público alcançável pelo MP.
- **Aprovação + reembolso** (sem webhook) dá pra validar por polling de
  `GET /v1/payments/{id}`: cobrança de R$ 1 com e-mail real → Marcos paga pelo
  app do banco → `approved` → `POST /v1/payments/{id}/refunds`. Movimenta
  dinheiro real (R2) — só com autorização explícita.

## Resolução (2026-09-28) — ciclo real de R$ 1 validado ponta a ponta

Sem aprovação fictícia possível, o ciclo foi validado com **uma transação real
controlada**, autorizada pelo Marcos:

| Etapa | Resultado | Webhook real (assinatura) |
|---|---|---|
| `POST /v1/payments` (payload idêntico ao `cobrar()`, e-mail real ≠ conta vendedora, sem `notification_url`) | `201`, id `180383866387`, `pending` | `payment.created` → **válida** |
| Marcos pagou pelo app do banco (copia-e-cola) | `approved / accredited`, líquido R$ 0,99 | `payment.updated` → **válida** |
| `POST /v1/payments/{id}/refunds` (mesma chamada do `reembolsar()`) | `201`; pagamento `refunded`, R$ 1,00 devolvido | 2× `payment.updated` → **válidas** |

- **Assinatura `x-signature` confere com o `MercadoPagoWebhookVerifier` de
  produção nas 4 notificações reais** — o bug do SAW HUB (HMAC nunca batia em
  pagamento real, Checkout Pro) **não se reproduz** aqui (Checkout Transparente,
  webhook do painel com só "Pagamentos (legacy)"). Validado pelo teste
  `MercadoPagoWebhookRealCaptureTest` (lê um JSONL de notificações capturadas;
  só roda com `MP_WEBHOOK_CAPTURE` + `MERCADOPAGO_WEBHOOK_SECRET`, nunca no CI) —
  reaproveitar sempre que o segredo ou a config do webhook mudar.
- Captura feita com receptor local + túnel temporário (cloudflared), desligados
  ao fim. Notificações chegam com `data.id` e `type=payment` na query **e** no
  corpo; `x-request-id` presente em todas.

### Pendências que saíram do teste
1. **URL do webhook no painel aponta pra um túnel que já não existe** — trocar
   pela URL definitiva do backend antes de ligar `MERCADOPAGO_ENABLED=true` em
   qualquer ambiente (a config antiga também estava errada: ngrok morto +
   eventos "Order"/"Vinculação", que nunca disparam pra `/v1/payments`).
2. **Nome do recebedor no Pix é `EDEHGFABC90774`** (apelido interno da conta MP,
   campo *merchant name* do BR Code) — o cliente vê isso no app do banco.
   Ajustar o nome de exibição da conta antes do lançamento.
3. **Tarifa no reembolso:** líquido recebido foi R$ 0,99 e o reembolso devolveu
   R$ 1,00 ao pagador — conferir no extrato se o MP estorna a tarifa ou se a
   plataforma absorve (impacta cancelamentos com escrow retido).
4. **Reembolso/devolução iniciado fora do sistema** (painel MP, devolução Pix
   MED) chega como `payment.updated` com status `refunded`, que o
   `MercadoPagoWebhookController` trata como no-op (só mapeia
   `approved`/`rejected`/`cancelled`) — a `Transaction` não refletiria. Hoje só
   o nosso Outbox dispara reembolso, então não é bug, mas é risco a decidir.

## Solução
**Para criação Pix real**, usar o e-mail verdadeiro do cliente, que não seja
conta de teste MP. O endpoint aceitou esse formato com o payload do gateway;
não foi identificado campo/header que precise ser adicionado para sanar o
403/4390. **Para simular aprovação fictícia com comprador de teste**, não há
correção client-side demonstrada para esta aplicação. O exemplo oficial de Pix
via Checkout Transparente limita o teste a uma cobrança pendente, sem
aprovação pelo QR. O e-mail da conta de teste foi recusado três vezes nesta
aplicação e o e-mail comum criou cobrança `live_mode: true`. Não alterar o
gateway para trocar automaticamente e-mails de teste por e-mails comuns:
isso criaria cobranças reais e esconderia a falha de homologação. Para validar
o ciclo completo do MKT-49, usar testes locais com gateway simulado e, quando
autorizado, uma transação real controlada; não declarar que houve aprovação
em sandbox. A confirmação de eventual exceção ou mecanismo novo para aprovação
fictícia em `/v1/payments` depende do suporte MP. Mensagem de suporte redigida
abaixo, com envio represado a critério do Marcos.

Não bloqueia o resto do projeto — só a validação fictícia e ponta a ponta do
MKT-49 (etapa 2 do "Quando retomar" no ADR). Não repetir tentativas sem novo
achado concreto (resposta do suporte ou mudança observada na conta).

### Corpo sugerido pro chamado de suporte
```
Aplicação: Onda Marketplace (appId 5313375069234064), Checkout Transparente
via API de Pagamentos.

Ao cobrar via POST /v1/payments (Pix) usando a credencial de PRODUÇÃO da
aplicação com payer.email de uma conta de teste (comprador), recebo sempre
403 "Payer email forbidden" (code 4390), mesmo criando o comprador de teste
na hora via POST /users/test_user (e-mail confirmado na resposta da própria
API, não adivinhado). Reproduzido com 2 compradores de teste diferentes.

Já descartei: Pix habilitado na conta (GET /v1/payment_methods lista pix),
endereço fiscal preenchido, credencial é APP_USR- (produção, não TEST-).

Evidência (timestamp;request-id de 3 tentativas):
- 29-09-2026T00:33:31UTC;4eaed1fc-549f-4c5f-97be-bf43cbc15ecf
- 29-09-2026T00:34:24UTC;4b9a0ce8-6ee6-4833-a6da-a4bc337d6199
- 29-09-2026T00:44:11UTC;072dfc46-3a03-4b75-952d-5e238e444bd8

O bloqueio de primeira cobrança da conta vendedora parece improvável: a mesma
aplicação criou um Pix real pendente com e-mail comum (detalhes abaixo).

A documentação oficial de Métodos Core da Payments API descreve exatamente
HTTP 403 "Payer email forbidden"
(code 4390) para payer.email de conta @testuser.com e sugere outro e-mail.
Esse texto está na seção de cartão, enquanto o guia de Pix via Payments API
não explica o código. A exigência de @testuser.com no roteiro de Pix é para
/v1/orders, não /v1/payments. Qual é a regra efetiva para Pix neste endpoint
e como testar uma cobrança fictícia sem criar uma cobrança de produção real?

Ensaio adicional na mesma aplicação: 2026-09-29T01:05:47Z,
x-request-id ef005781-1374-4249-bbea-fba31d28a07a. Com um alias de e-mail
controlado, que não é conta de teste MP, POST /v1/payments criou um Pix com
HTTP 201, payment ID 180381125045, pending e live_mode=true. Nenhum Pix pago.
O exemplo oficial de Pix via Checkout Transparente informa que, mesmo quando
um usuário de teste consegue criar Pix pendente e receber QR Code, não é
possível pagar esse código para aprovar o teste. Para esta aplicação, existe
algum procedimento atual e suportado para criar e aprovar Pix fictício em
POST /v1/payments? Em caso negativo, confirmem a restrição 403/4390 para
@testuser.com e a necessidade de transação real controlada para validar o
ciclo completo dessa API. Não queremos migrar para /v1/orders.
```

## Ligado a
- [[mercadopago-escrow-modelo-de-repasse]]
