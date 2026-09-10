---
tipo: decisao
data: 2026-09-10
status: Ativa
---

# Integração Mercado Pago — qual modelo de retenção/repasse usa o Escrow (MKT-49)

## Contexto

`MKT-49` (caminho crítico da Fase 5) troca o `GatewayServiceImpl` stub por integração
real com o Mercado Pago. A costura já existe e é limpa — só três pontos mudam:

| Ponto de integração | Hoje (stub) | Com MP |
| --- | --- | --- |
| `GatewayService.cobrar(tx)` | devolve `gw-stub-<uuid>` | cria cobrança Pix/cartão no MP, devolve `payment.id` |
| webhook `POST /api/v1/payments/webhook` | `SimulatedGatewayCallback` varre e confirma | MP chama de verdade; traduz notificação → `confirmPayment(id, "PAGO"\|"REJEITADO")` |
| `GatewayService.liberar(tx)` / `reembolsar(tx)` | só loga | repassa ao prestador (menos comissão) / estorna |

O motor Saga/Outbox (`OutboxProcessor` sem `@Transactional`, `Transaction` com máquina
`PENDENTE → RETIDO → LIBERADO|REEMBOLSADO` guardada na entidade) **não muda** e já
atende TS02. A máquina de estados do chamado **não muda**.

O que a **spec exige** (não é opção de implementação):

- **US06 (Escrow):** o dinheiro fica **retido** após o pagamento; falha de rede com o
  gateway não faz rollback de banco — reconciliação por evento.
- **US07:** o prestador vê "pago e retido" antes de executar.
- **US17:** o **split só acontece na conclusão** — `CONCLUIDO` confirmado → comissão
  (10–25%) pra plataforma, restante pro prestador, `transaction → LIBERADO`.
- **US18:** disputa → mediação decide `LIBERADO` (split) ou `REEMBOLSADO` (devolve ao
  cliente), pelo mesmo motor Outbox.
- **US19 (linha 140):** o valor bruto nunca é repassado direto ao prestador
  (anti-desintermediação — protege a comissão do MVP).

## O problema que trava a decisão

O **Split de Pagamentos 1:1** do Mercado Pago (`application_fee` no `POST /v1/payments`)
divide o dinheiro **automaticamente na aprovação** e credita a parte do vendedor na
conta MP dele seguindo a política de liberação do MP — **Pix liquida no mesmo dia**.
Não existe, no split básico, um botão de "segura a parte do vendedor e libera quando
eu mandar". Ou seja: **o split básico do MP não é o nosso Escrow** — ele quebra US06
(dinheiro não fica retido pela plataforma) e US17 (split sai na hora do pagamento, não
na conclusão).

Sobram dois modelos que atendem a spec. A escolha é do humano porque muda o tipo de
conta MP, a exposição regulatória do CNPJ e o fluxo de caixa — não é detalhe técnico.

### Modelo A — Plataforma recebe 100% e repassa na conclusão

- `cobrar`: cobrança Pix/cartão **na conta MP da plataforma**, valor cheio. Webhook
  `approved` → `tx.reter()` (o dinheiro está no saldo MP da plataforma = "retido").
- `liberar`: a plataforma faz um **repasse** (transferência Pix / `POST /v1/payments`
  com `application_fee` invertido, ou payout) de `valor − comissão` pro prestador.
- `reembolsar`: `POST /v1/payments/{id}/refunds` (total) no pagamento original.
- **Prestador precisa:** só uma chave Pix cadastrada no perfil. Sem OAuth.

**Prós:** menor esforço (sem fluxo de conexão de conta), onboarding de prestador
trivial, mapeia 1:1 na máquina atual (`RETIDO` = saldo nosso).
**Contras:** o CNPJ passa a **custodiar dinheiro de terceiros** transitoriamente —
isso é arranjo de pagamento e atrai atenção do BACEN se escalar (aceitável num piloto
de 1 bairro, arriscado como modelo permanente). 100% do GMV transita pela conta e pelo
resultado contábil da plataforma (risco de gross-up na receita). Repasse é ação ativa
com custo/tarifa e ponto de falha operacional.

### Modelo B — Marketplace MP nativo com liberação controlada

- Cada **prestador conecta a conta Mercado Pago** via OAuth (`/authorization` →
  `POST /oauth/token`, guardamos o `access_token`/`user_id` do vendedor).
- `cobrar`: `POST /v1/payments` com `application_fee` (comissão) **em nome do
  vendedor**, com o MP **retendo** a parte do vendedor (liberação controlada pelo
  marketplace).
- `liberar`: a plataforma chama a **liberação** da parte retida do vendedor.
- `reembolsar`: refund no pagamento; MP desfaz o split.
- **Prestador precisa:** conta Mercado Pago (grátis, criação rápida) + passar pelo
  OAuth uma vez.

**Prós:** modelo "correto" de marketplace — a plataforma **nunca custodia** o dinheiro
do cliente (fica na estrutura licenciada do MP); só a comissão entra no resultado da
plataforma; repasse é do MP.
**Contras:** +2–3 dias de dev (tela/rota de conexão de conta, storage de token do
vendedor, refresh), fricção no onboarding do prestador, depende de `MKT-50` (conta MP
empresarial + produto "Split"/marketplace liberado). Confirmar com o MP se a
**liberação controlada** está disponível pra Pix nesse produto (o split automático
está; o controle de retenção precisa de confirmação com o suporte/conta).

## Decisão

**Modelo A — a plataforma recebe o valor cheio e repassa ao prestador na conclusão.**
Escolhido pelo humano em 2026-09-10 para o piloto (1 bairro, urgência de lançamento).

- `cobrar`: `POST /v1/payments` (Pix) na conta MP **da plataforma**, valor cheio,
  `X-Idempotency-Key` = `transaction.idempotency_key`. Guarda `payment.id` como
  `gateway_transaction_id`.
- webhook MP: valida `x-signature`, `GET /v1/payments/{id}`, mapeia `status`
  (`approved`→PAGO, `rejected`/`cancelled`→REJEITADO), chama
  `PaymentService.confirmPayment`. `approved` → `tx.reter()` (dinheiro no saldo MP da
  plataforma = retido).
- `liberar`: repasse Pix de `valor_total − valor_comissao` para a **chave Pix do
  prestador** (cadastrada no perfil, cifrada em repouso). Idempotente pela referência
  da transação.
- `reembolsar`: `POST /v1/payments/{gateway_transaction_id}/refunds` (total) no
  pagamento original.
- Só **Pix** no piloto. Cartão fica pra depois (`PaymentMethod.CARTAO` já existe no
  enum, sem fluxo real).

**Dívida técnica assumida:** migrar pro **Modelo B** (marketplace MP nativo, sem
custódia pela plataforma) antes de escalar pra mais bairros ou antes de captação —
o que vier primeiro. Registrar aqui quando isso for repriorizado.
Gatilho de reavaliação: veto do advogado/investidor a custódia transitória, ou 2º
bairro entrando.

Itens comuns aos dois modelos (não dependem da escolha, podem começar já):

- Config: `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET` (assinatura
  `x-signature`: HMAC-SHA256 sobre `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`),
  `MARKETPLACE_COMISSAO` (já existe). Sandbox: credenciais de teste saem na hora.
- Novo endpoint MP-específico do webhook (ou adaptador no atual) que valida
  `x-signature`, resolve `data.id` → `GET /v1/payments/{id}`, mapeia `status`
  (`approved`→PAGO, `rejected`/`cancelled`→REJEITADO) e chama `confirmPayment`. O
  `POST /api/v1/payments/webhook` atual (segredo compartilhado) continua pra testes.
- Idempotência: `X-Idempotency-Key` em toda chamada de escrita ao MP (usar a
  `idempotency_key` da `Transaction`).
- `SimulatedGatewayCallback` + `marketplace.gateway.simulate-confirmation` continuam
  existindo, desligados em produção — são o que roda no CI e na demo.
- Cartão fica pra depois: o piloto vai **só com Pix** (US05 cita os dois; `PaymentMethod.CARTAO`
  já existe no enum mas não precisa de fluxo real pro lançamento).

## Consequências

- **Não** mexer no `OutboxProcessor`, na máquina de `Transaction`, nem na máquina de
  `service_requests` — a integração é atrás do `GatewayService` e do webhook. Qualquer
  PR de MKT-49 que toque essas classes está fora do escopo e provavelmente errado.
- **Nunca** chamar o SDK/HTTP do MP dentro de `@Transactional` (TS02 — o motivo do
  Outbox existir).
- Chave Pix do prestador (Modelo A) é **PII** → cifrar em repouso como o CPF.
- `MARKETPLACE_GATEWAY_SIMULATE=true` **jamais** em produção — é o que faz o backend
  inventar confirmação de pagamento. Já tem guarda (`@ConditionalOnProperty`, default
  false, `@PostConstruct` que loga aviso).
- Go-live precisa só de credencial de **produção aprovada** do MP; todo o
  desenvolvimento e QA rodam em **sandbox** sem bloquear.
- Se for Modelo B: token do vendedor é credencial de terceiro → cofre/cifra, nunca em
  log, refresh antes de expirar.

## Ligado a

- [[escrow-guardas-gateway-simulado]]
- [[pagamento-confirmacao-fake]]
- [[testes-que-mentem]]
