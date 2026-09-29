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
  **Atualização 2026-09-13:** confirmado com o humano que a conta **ainda não tem**
  payout/transferência Pix programática habilitada no Mercado Pago (deve passar a
  ter). Enquanto isso, `liberar` lança `ManualPayoutRequiredException` — o evento cai
  em `FALHA` e vira **fila de repasse manual** no admin
  (`GET/POST /admin/transactions/{id}/repasse-pendente|confirmar-repasse-manual`,
  commit `3e83dae`): o operador vê a chave Pix decifrada, paga fora do sistema e
  confirma, o que libera o escrow sem chamar o gateway de novo. Trocar por payout
  automático quando o MP habilitar é só reescrever `liberar()` — nada mais no fluxo
  muda.
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

## Estado atual (2026-09-14) — código pronto, validação ao vivo pausada por decisão do humano

PR #6 mesclado no master (`2cd7b13`): cobrança Pix, webhook, chave Pix do prestador,
reembolso e fila de repasse manual implementados e testados (`MockRestServiceServer`),
tudo atrás de `marketplace.gateway.mercadopago.enabled=false` (default) — zero mudança
de comportamento em CI/demo/produção atuais.

**Falta só a confirmação ao vivo em sandbox**, e ela está bloqueada numa decisão de
produto, não numa pendência técnica: ativar as credenciais de produção do Mercado
Pago (necessárias mesmo pra sandbox — ver `mercadopago-sandbox-*` no `onda-starter`)
travou num erro genérico do painel deles, e o humano decidiu **não ativar agora**
(2026-09-14). Isso é uma pausa deliberada, não um bloqueio esquecido — não reabrir
sozinho; só retomar quando o humano pedir.

Quando retomar: (1) ativar credencial de produção; (2) rodar o roteiro de sandbox
com essa mesma credencial (não existe credencial `TEST-` pra esse fluxo); (3) só
depois construir a tela de QR/copia-e-cola do Pix pro cliente (evitar adivinhar o
formato da resposta do MP sem ver uma real).

### Retomada (2026-09-28) — wizard e roteiro de sandbox corrigidos com achado cross-projeto

A menção anterior a "recriar a aplicação escolhendo API de Orders" estava **errada
pro nosso caso** — vinha do bug `DXT40` do `confidencial-calcados`
(`memoria-tecnica/decisoes/credenciais-mercado-pago-onboarding.md` daquele projeto),
que integra via **Checkout Pro** (redireciona pro site do MP). Nosso backend
(`MercadoPagoGatewayService.java:107`) chama `POST /v1/payments` **direto** —
perfil **Checkout Transparente**. Uma nota de 2026-09-13 no `onda-starter`
(`memoria-tecnica/decisoes/mercadopago-criar-aplicacao-checkout-transparente.md`)
documenta exatamente esse perfil e é explícita: escolher "API de Orders" aqui
exige reescrever a integração contra outro contrato (`/v1/orders`) e desalinha os
eventos de webhook.

**Wizard "Criar aplicação" — resposta certa pro nosso caso:**
- Tipo de pagamento: Pagamentos online
- Como criou a loja: Com um desenvolvimento próprio
- Solução de pagamento: **Checkout Transparente**
- API: **API de Pagamentos** (a "versão anterior" no wizard — não a recomendada)
- Webhook a assinar: **"Pagamentos (legacy)"** — não "Order (Mercado Pago)"

> **Superado no mesmo dia — não seguir o roteiro abaixo.** Em `/v1/payments`,
> `payer.email` `@testuser.com` dá `403/4390` e Pix não tem aprovação fictícia; a
> validação foi feita com transação real controlada (ver "Resolvido" mais abaixo e
> [[mercadopago-payer-email-forbidden-sandbox]]). Mantido só como histórico.

**Roteiro de sandbox — mecânica real (`onda-starter/memoria-tecnica/bugs/mercadopago-sandbox-exige-credencial-de-producao.md`,
achado 2026-09-13):** credencial `TEST-` **não é suportada** em `POST /v1/payments`
(a própria API recusa com `invalid_credentials`). O sandbox desse fluxo é: usar a
credencial de **produção** (`APP_USR-...`) + `payer.email` de um **usuário de
teste** (`test_user_...@testuser.com`, criado em Contas de teste ou
`POST /users/test_user`) + `payer.first_name: "APRO"` pra aprovação automática —
o que torna a transação fictícia é o comprador ser de teste, não a credencial.
Nunca usar `payer.email` com domínio `.test`/`.example` (o MP rejeita com 400 —
`onda-starter/memoria-tecnica/bugs/mercadopago-sandbox-rejeita-email-dominio-test.md`).
Ativar a credencial de produção exige preencher "Setor" e "Site" no painel (serve
URL temporária/preview; trocar quando o domínio real existir).

Variáveis a setar (`application.yml:87-93`, nunca no Git): `MERCADOPAGO_ENABLED=true`,
`MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET`, `MERCADOPAGO_NOTIFICATION_URL`.

**Atualização (2026-09-28) — sandbox travado num 403 novo, chamado de suporte pronto:**
credencial de produção ativada (app "Onda Marketplace", appId `5313375069234064`),
mas `POST /v1/payments` recusa qualquer comprador de teste com `403 Payer email
forbidden` (code 4390) — detalhe completo, causas descartadas e corpo do chamado
de suporte em [[mercadopago-payer-email-forbidden-sandbox]].

**Resolvido no mesmo dia (2026-09-28):** o 403 era restrição documentada do MP a
`payer.email` `@testuser.com` em `/v1/payments` — e Pix fictício não tem
aprovação simulada nessa API. Etapa 2 do "Quando retomar" cumprida com uma
**transação real controlada de R$ 1** (cobrança → pagamento → reembolso), com as
4 notificações reais de webhook validando a assinatura no verificador de
produção. Etapa 3 destravada: formato real da resposta Pix conhecido
(`point_of_interaction.transaction_data.qr_code` / `qr_code_base64` /
`ticket_url`). Pendências (URL definitiva do webhook no painel, nome do
recebedor, tarifa no reembolso) no arquivo do bug.

**Etapa 3 concluída (2026-09-29):** `MercadoPagoGatewayService.cobrar()` agora
grava `pixQrCode`/`pixQrCodeBase64`/`pixTicketUrl` na `Transaction` (migration
`V19__transaction_pix_dados.sql`), expostos via `TransactionDto`.
`PaymentPixScreen` no mobile deixou de mostrar o QR mockado/chave hardcoded —
faz polling (`pollPixDados`) e renderiza a imagem base64 real + copia-e-cola
real, com fallback sem travar o botão "Paguei" quando o gateway é o stub
(demo/CI). As "3 etapas" do "Quando retomar" desta seção estão todas
concluídas.

## Ligado a

- [[mercadopago-payer-email-forbidden-sandbox]]
- [[escrow-guardas-gateway-simulado]]
- [[pagamento-confirmacao-fake]]
- [[testes-que-mentem]]
