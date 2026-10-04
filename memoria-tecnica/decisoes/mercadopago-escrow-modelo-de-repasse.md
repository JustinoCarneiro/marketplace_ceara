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

**Dívida técnica assumida — OBSOLETA desde 2026-09-29.** O texto original (2026-09-10)
dizia: *"migrar pro Modelo B (marketplace MP nativo, sem custódia pela plataforma)
antes de escalar pra mais bairros ou antes de captação"*. O suporte do MP confirmou por
escrito que o Split **não retém Pix** (seção "Resposta do suporte MP (2026-09-29)",
abaixo): o Modelo B não entrega escrow com Pix, então não há migração a fazer — a
alternativa foi **descartada**, não adiada. No Mercado Pago, o único caminho que retém
Pix é o próprio Modelo A (a plataforma recebe e repassa).

O que continua em aberto de verdade:

- **Custódia transitória** segue sendo da plataforma. O gatilho de reavaliação original
  (veto do advogado/investidor à custódia, ou 2º bairro entrando) continua valendo, mas
  agora aponta pra reavaliar a **estrutura** (PJ, arranjo de pagamento, parceiro
  licenciado) com o advogado — ver `docs/PENDENCIAS_JURIDICAS.md` — e não pra trocar de
  modelo dentro do MP.
- **Repasse automático** depende da habilitação do Money Out (chamado WCS-52692); o
  plano está em "Plano de implementação do repasse automático", no fim deste ADR.

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
- ~~Se for Modelo B: token do vendedor é credencial de terceiro → cofre/cifra, nunca em
  log, refresh antes de expirar.~~ Sem efeito: Modelo B descartado em 2026-09-29 (ver
  "Dívida técnica assumida" acima). Ficou o princípio, caso OAuth de vendedor volte por
  outro motivo: token de terceiro nunca em log, sempre cifrado.

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

## Repasse automático — pergunta ao suporte MP antes de migrar pro Modelo B (2026-09-29)

Repasse manual funciona hoje, mas o Marcos quer automático já. Pesquisa (não só
busca — conferido o índice oficial de `mercadopago.com.br/developers/pt/reference`)
não achou nenhuma API pública do MP pra "mandar Pix pra uma chave externa
qualquer" a partir da conta da plataforma — o endpoint de disburses de Advanced
Payments referenciado em buscas devolve 404. O único mecanismo real de
marketplace que o MP documenta é o **Split de Pagamentos 1:1**
(`marketplace_fee`/`application_fee`), que exige o prestador ter **conta MP
própria vinculada via OAuth** — isso é o Modelo B da seção "Decisão" acima, não
um endpoint a mais no Modelo A atual.

Comparado com o padrão real de Uber/99/iFood (pesquisado 2026-09-29): todos
resolvem com o prestador tendo uma conta/carteira própria (conta digital
própria ou BaaS — Celcoin/Dock/QI Tech/Matera) — nenhum "manda dinheiro pra
qualquer chave" via API simples. Reforça que Modelo B (reaproveitando a conta
MP que já validamos com dinheiro real) é mais barato que trazer um BaaS novo
pro piloto — BaaS também exige onboarding do prestador, não é atalho.

**Bloqueio antes de migrar:** a linha 66-69 desta ADR ("Confirmar com o MP se a
liberação controlada está disponível pra Pix nesse produto") nunca foi
respondida. Sem isso, não dá pra saber se o Split com Pix retém até a
conclusão do serviço (o que a spec exige — US06/US17) ou libera pro prestador
na hora da aprovação (quebra o Escrow). Pergunta redigida, pronta pra enviar
(canal: suporte/gerente de conta MP, não o formulário de bug):

```
Contexto: marketplace de serviços residenciais, piloto em 1 bairro. Hoje
cobramos via Checkout Transparente / API de Pagamentos (POST /v1/payments
direto, sem application_fee) — a plataforma recebe o valor cheio e repassa
manualmente ao prestador na conclusão do serviço.

Queremos migrar pro Split de Pagamentos 1:1, com cada prestador conectando a
própria conta MP via OAuth, pra automatizar esse repasse. Antes de migrar,
preciso confirmar um requisito de negócio que não achei documentado: o valor
pode ficar RETIDO (não repassado ao vendedor) entre a aprovação do pagamento e
um momento posterior que nós decidimos (quando o cliente confirma que o
serviço foi concluído — pode levar horas ou dias)? Ou o application_fee no
Split sempre libera a parte do vendedor automaticamente na aprovação do
pagamento, sem forma de reter?

Isso precisa funcionar especificamente com Pix (payment_method_id: "pix" +
application_fee via checkout transparente/API de pagamentos, não Checkout
Pro). Se não for possível reter, qual produto do Mercado Pago resolve
"custodiar até uma confirmação posterior, depois splitar automaticamente"?
```

### Resposta do suporte MP (2026-09-29) — Modelo B descartado, caminho é Money Out

Suporte confirmou por escrito: **Split (OAuth + `application_fee`) não retém Pix**
— Pix liquida na aprovação, sem "release on demand" pela plataforma. O único
hold nativo da API de Pagamentos é `capture=false`, exclusivo de **cartão**
(autoriza → captura em dias); não existe equivalente pra Pix. **Modelo B, do
jeito que a seção "Decisão" desenhou, está descartado — não dá pra resolver
com configuração, é limitação do produto.**

O que o suporte recomendou é, na prática, **o Modelo A que já está implementado**
(cobrar na própria conta, reter, repassar depois) — só falta a perna do repasse,
que no ecossistema MP é o produto **Money Out**:

Endpoint real (corrigido pelo texto cru da doc — detalhes em "3ª resposta" abaixo):
`POST https://api.mercadopago.com/v1/transaction-intents/process`, uma transferência
por chamada, com o **documento do titular do destino** no corpo. (Este bloco dizia
`/v1/payouts` — veio de um resumo automático da doc que estava errado.)

**Isso encaixa no Modelo A sem tocar no lado do cliente** — cobrança, escrow,
`PaymentPixScreen`, máquina de estados, nada disso muda. Só reescreve
`MercadoPagoGatewayService.liberar()` (hoje lança `ManualPayoutRequiredException`)
pra chamar o Money Out de verdade.

**Bloqueio real, não técnico:** Money Out **exige autorização comercial prévia**
do Mercado Pago antes de poder integrar — o suporte não detalhou prazo nem
critério nessa resposta. Próximo passo é solicitar essa autorização, não
escrever código (chamar o Money Out em produção sem ela deve dar erro de permissão).

**Observação à parte, não bloqueante:** o suporte mencionou que a documentação
atual recomenda Checkout API via **Orders** como padrão pra Brasil/MLB, mesmo
pra "experiência transparente" — contradiz a nota anterior desta ADR
(`onda-starter`, 13/09) que orientou API de Pagamentos. Não é motivo pra migrar
agora (nossa integração via Pagamentos já está validada com dinheiro real) —
só registrar a divergência caso vire relevante depois.

### 2ª resposta do suporte MP (2026-09-29) — como pedir a autorização do Money Out

Não há formulário público, prazo nem piso de volume documentados — a análise é
comercial. Costuma ser pedido: dados do negócio (CNPJ/razão social/nome fantasia
da conta de origem), descrição do modelo, volumetria (transações por dia/mês,
ticket médio), políticas operacionais (quando paga, reembolsa, trata disputa) e
controles de risco (validação de prestadores, antifraude, validação da chave Pix
do recebedor). Perguntaram se a conta de origem é **PJ ou PF** e qual o ticket
médio/frequência dos repasses.

**Estado real (verificado 2026-09-29):** a conta MP dos testes é **pessoa
física** (`GET /users/me` → `mercadopago_account_type: "personal"`, identificada
por CPF, sem dados de empresa). A PJ que vai operar o marketplace ainda não
existe nos documentos (`docs/PENDENCIAS_JURIDICAS.md` §4: razão social/CNPJ "a
definir") — o pedido do Money Out provavelmente depende disso, e é a mesma PJ
dos Termos/Privacidade.

**Controles — o que existe × o que falta** (não afirmar ao suporte o que não
existe):
- Existe: CPF único (`cpf_hash`), bloqueio de auto-contratação, aprovação/reprovação
  manual de prestador pelo admin, avaliação double-blind, chat com mascaramento de
  contato, chave Pix cifrada em repouso, reembolso/disputa pelo Outbox.
- **Não existe:** background check automático (`BackgroundCheckServiceImpl` é
  stub, ROADMAP §4 M02) e **verificação de titularidade da chave Pix** (a chave
  não é conferida com o CPF cadastrado nem com o DICT). Até 2026-09-29 também
  não havia validação de formato — corrigido, ver abaixo.

**Feito em 2026-09-29 (independente da autorização):** `PixKey`
(`provider/PixKey.java`) valida formato + dígito verificador na entrada (CPF,
CNPJ numérico e alfanumérico da Receita desde jul/2026, e-mail, telefone `+55…`,
chave aleatória), guarda a forma canônica e deriva o tipo. Vale também pro
repasse manual de hoje (chave digitada errada = repasse pra pessoa errada).
`ProviderService.atualizarChavePix` passou a rejeitar chave malformada com
`PIX_KEY_INVALID`. Chaves cadastradas antes ficam como estavam (sem migração);
o repasse deve passar por `PixKey.parse` também na hora de pagar.

**Decidido não fazer (ainda):** exigir que chave CPF bata com o CPF cadastrado.
O CPF do cadastro não é verificado (background check é stub), então a conferência
daria segurança aparente e restringiria chave de outra pessoa / CNPJ de MEI sem
decisão de política. Fica pra depois da resposta do MP sobre titularidade (DICT).

### 3ª resposta do suporte MP + texto cru da doc `money-out/*` (2026-09-29)

**Fonte de verdade:** as páginas em markdown cru
(`https://www.mercadopago.com.br/developers/pt/docs/money-out/<pagina>.md` — `prerequisites`,
`integration-configuration`, `integration-test`, `end-to-end-encryption`). **Não usar
resumo de WebFetch pra doc de API:** o resumidor inventou `/v1/payouts`, lote de 1000 e o
formato `transactions[].pix`, que não existem no texto (erro nosso, corrigido no mesmo
dia; o suporte estava certo).

- **PF x PJ:** a doc pública não diz se a autorização comercial exige PJ e o suporte não
  soube cravar. Produção pede controles mais "enterprise", junto com análise
  comercial/compliance. **Podem adiantar a implementação e os testes em ambiente de teste
  enquanto a empresa é estruturada.**
- **Pré-requisitos:** autorização da área comercial; app de "Pagamentos online" com
  **Checkout Transparente** (é o nosso); credenciais de teste e de produção; **conta MP de
  origem com saldo**; **chave Pix da conta de origem cadastrada** (a nossa já existe — é a
  que aparece no copia-e-cola).
- **Endpoint (Pix):** `POST https://api.mercadopago.com/v1/transaction-intents/process`,
  **uma conta de destino por chamada**. Headers: `Authorization: Bearer`, `Content-Type`,
  `X-Idempotency-Key`, `x-enforce-signature` (`false` em teste; `true` obrigatório em
  produção) e `x-Signature` (só produção).
- **Corpo:**
  ```json
  { "external_reference": "≤64 chars [A-Za-z0-9_-], único",
    "point_of_interaction": { "type": "PSP_TRANSFER" },
    "seller_configuration": { "notification_info": { "notification_url": "…" } },
    "transaction": {
      "from": { "accounts": [ { "amount": 100 } ] },
      "to":   { "accounts": [ { "type": "current", "amount": 100,
                 "chave": { "type": "CPF|CNPJ|EMAIL|PHONE|PIX_CODE", "value": "…" },
                 "owner": { "identification": { "type": "CPF|CNPJ", "number": "…" } } } ] },
      "total_amount": 100 } }
  ```
  `from.amount == to.amount == total_amount`. `to.accounts[].type`: `current`
  (Pix/conta bancária) ou `mercadopago`; o exemplo de teste da própria doc omite `type`
  no Pix. A **unidade de `amount` não é explícita** (exemplos "100,00" e `100`) —
  confirmar no modo de teste/MP antes de qualquer valor real (reais × centavos).
- **`PIX_CODE`:** a doc só lista o valor, **sem definir** (o suporte tinha razão). Nosso
  UUID (chave aleatória/EVP) provavelmente é isso, mas confirmar.
- **Titularidade:** o documento do titular do destino é **obrigatório**
  (`owner.identification`). A doc não diz que o MP confere chave × documento — perguntar.
  Temos o CPF do prestador cifrado no cadastro; chave CNPJ exigiria enviar o CNPJ (que não
  guardamos).
- **Resposta:** `202` com `id` e `status`; se `pending`, consultar
  `GET /v1/transaction-intents/{id}`. Status: `processed`/`approved` (sucesso);
  `rejected` com `status_detail` `by_bank`, `by_provider`, `high_risk`,
  `insufficient_funds`, `other_reason`, `review_manual`; `refunded`; em andamento:
  `transaction_in_process` (`pending_authorized`, `pending_bank`). A página de teste usa
  outro vocabulário (`new`, `failed`, `partially_processed`, `reverted`) — a doc é
  inconsistente entre páginas, então tratar status desconhecido como "não confirmado".
- **Notificações:** webhook em `seller_configuration.notification_info.notification_url`
  (`transaction_intent.created` / `.updated`, `data.id`); a doc manda **validar buscando a
  transação** (`GET`) e responder 200/201; reenvios em 15 min, 30 min, 6 h, 48 h, 96 h. Não
  diz como o webhook é assinado.
- **Assinatura (só produção):** Ed25519 — `openssl genpkey -algorithm ed25519 -out
  mpprivate.pem` + `openssl pkey -in mpprivate.pem -pubout -out mppublic.pem`; assina-se
  **o corpo da requisição** com a chave privada, em **Base64**, no header `x-Signature`. A
  chave pública vai para a **equipe de Integrações** (fora da API, acoplado à autorização).
  Java 21 tem Ed25519 nativo (`Signature.getInstance("Ed25519")`).
- **Teste:** Access Token de **teste** + header `X-Test-Token: true`, no **mesmo**
  `POST /v1/transaction-intents/process`; transações transitórias (não armazenadas). **O
  `external_reference` escolhe o resultado simulado:** `new`, `failed_by_bank`,
  `failed_by_provider`, `failed_by_caps`, `failed_other_reason`, `failed_by_high_risk`,
  `failed_by_compliance`, `failed_insufficient_funds`, `partially_processed`,
  `partially_processed_pending_bank`, `reverted`, `partially_reverted_partially_refunded`,
  `timeout` (responde `processed` só após 2 min), `internal_server_error` (500); qualquer
  outro valor → `processed`. Dá pra exercitar todos os caminhos de falha do nosso código
  sem mover dinheiro. Não está documentado se a autorização comercial é exigida antes até
  do teste — só tentando pra saber.
- **Consequência:** dá pra desenvolver e testar em modo de teste sem PJ; **produção só
  depois da autorização + envio da chave pública**. Antes de implementar `liberar()` real:
  (1) confirmar o request contra o modo de teste; (2) decidir a confirmação assíncrona — o
  `202` só significa "aceito", e hoje o `OutboxProcessor` marca `LIBERADO` assim que
  `liberar()` retorna, o que num repasse assíncrono marcaria como pago dinheiro que ainda
  pode falhar.

### 4ª resposta do suporte MP (2026-09-29) — as 4 dúvidas técnicas

| Dúvida | Resposta do suporte | Certeza |
|---|---|---|
| Unidade de `amount` | **Reais**, número decimal com ponto (`10` = R$ 10,00; `10.50`) | Não é explícita na doc — confirmar na 1ª transferência real, de valor mínimo, pra chave nossa |
| Confere chave × `owner.identification`? | **Não documentado**; sem status específico pra "documento não bateu" | Não dá pra contar com o MP pra barrar chave de terceiro — o controle é nosso (`PixKey` + fallback manual) |
| Webhook do Money Out é assinado? | **Não descrito.** O `x-signature` `ts=…,v1=…` (HMAC com secret da app) é o dos webhooks gerais de "Suas integrações", não confirmado pro Money Out | Usar a notificação só como **gatilho** e confirmar sempre com `GET /v1/transaction-intents/{id}` (é o que a doc manda) |
| `PIX_CODE` | "Esperado" ser chave aleatória (EVP) — inferência do suporte, a doc não define | Provisório: `PixKey.Tipo.ALEATORIA → PIX_CODE`; se falhar, o erro é rejeição, não perda de dinheiro |

Ofereceram ajudar a mapear erros com 1–2 respostas reais do modo de teste (sem dados pessoais).

### Tentativa em modo de teste (2026-09-29): 403 por política — a autorização comercial é exigida até no teste

Com o Access Token de teste do app (válido: `GET /users/me` → 200) e `X-Test-Token: true`,
tanto `POST /v1/transaction-intents/process` quanto `GET /v1/transaction-intents/{id}` (id
qualquer) devolvem `403` `{"blocked_by":"PolicyAgent","code":"PA_UNAUTHORIZED_RESULT_FROM_POLICIES",
"message":"At least one policy returned UNAUTHORIZED."}`. Não depende do corpo (com/sem
`type: current`; cenários `new` e `processed`). `x-request-id:
487fd3d7-f6ea-4afb-8268-413b89bb0560` (2026-09-29T21:26:54Z, app `5313375069234064`).

**Leitura:** o recurso `transaction-intents` está bloqueado por aplicação até a área comercial
autorizar — **não há sandbox sem autorização**. Consequência: o passo de "testes de contrato
com respostas reais" fica bloqueado, e **não implementar `liberar()` só com mocks** (o mock
validaria a nossa própria suposição do contrato — o padrão "teste que mente"). O trabalho
técnico só continua depois da autorização.

**Chamado aberto (2026-09-29):** pedido de autorização comercial do Money Out registrado no
suporte do MP (**WCS-52692**). Pedido: encaminhar à área comercial; dizer se **PF** pode ser autorizada no piloto;
quais dados/documentos a análise exige; prazo estimado. Acompanhamento: Central de
atendimento (`developers/pt/support/center/tickets`). Sem resposta, o repasse segue manual.

**Resposta inicial ao chamado WCS-52692 (2026-09-29) — triagem, não a decisão:**
- Confirmam o diagnóstico: o `403` **não** é problema de token nem de integração; é de natureza
  comercial. `/v1/transaction-intents/process` faz parte do Money Out, que exige **habilitação
  comercial prévia, inclusive em homologação**; sem ela o PolicyAgent bloqueia todas as chamadas,
  independente de configuração técnica.
- **PF:** não tem habilitação automática. A elegibilidade para piloto (inclusive operar sem CNPJ
  enquanto a empresa é constituída) é avaliada caso a caso pela equipe comercial — o canal de
  suporte **não confirma nem nega**.
- Documentos exigidos e prazo de análise: definidos pela equipe de Money Out, indisponíveis nesse canal.
- Caso categorizado como **escalada para a equipe comercial de Money Out**. **Aguardando o retorno
  deles** (elegibilidade PF, documentação, prazo). Naquele momento, nada a enviar.

### Atualização do chamado WCS-52692 (2026-10-02) — respondido; aguardando a equipe comercial

O suporte reiterou que o `403 PA_UNAUTHORIZED_RESULT_FROM_POLICIES` é bloqueio de habilitação comercial do
Money Out, sem falha no access token, e pediu dados operacionais do piloto. **Respondido em 2026-10-02**; o
chamado está em "Waiting for support". Ainda **não há decisão** sobre elegibilidade de conta PF, documentos
exigidos ou prazo. O que foi pedido e respondido (volumetria, controles) fica no registro privado do projeto,
não neste repositório público.

**Risco técnico local (migração PF → PJ):** hoje o gateway usa um único access token e o verificador de
webhooks, um único secret; a transação guarda `payment.id`, mas não identifica a conta de origem. A troca
direta das credenciais poderia afetar reembolsos e notificações de pagamentos antigos. Resolver esse ponto
antes de qualquer migração de conta, depois que o MP esclarecer o procedimento comercial.

### Plano de implementação do repasse automático (proposto — a aprovar; R2)

Só depois de: (a) rodar cenários no modo de teste com o token de teste (**hoje bloqueado — ver a tentativa acima**) e (b) revisão cruzada
(Codex) do plano/diff. Produção só depois da PJ + autorização comercial + chave pública
Ed25519 enviada ao MP.

1. **Flag** `marketplace.gateway.mercadopago.money-out.enabled=false` por padrão: desligada,
   `liberar()` continua lançando `ManualPayoutRequiredException` (fila manual atual).
2. **Pedido:** `amount = valorTotal − valorComissao` (2 casas); destino = chave do prestador
   decifrada e revalidada por `PixKey.parse`; `owner.identification` = CPF do prestador
   (decifrado). Chave **CNPJ** ou CPF ausente → fila manual (não guardamos o CNPJ).
   `external_reference` = id da transação (≤ 64, só `[A-Za-z0-9_-]`, único);
   `X-Idempotency-Key = <idempotencyKey>:repasse`.
3. **Confirmação assíncrona (a decisão):** `202` só significa "aceito". Recomendado: guardar
   `payout_intent_id` + `payout_status` na `Transaction` (migration nova) e **só marcar
   `LIBERADO` quando confirmar** (`processed`) por webhook + `GET`; `rejected`/`failed` →
   permanece `RETIDO` e cai na fila de repasse manual com o motivo. Isso exige o
   `OutboxProcessor` deixar de chamar `tx.liberar()` incondicionalmente no
   `PAYMENT_RELEASED` — mexe numa classe que este ADR pedia pra não tocar, justificado
   porque o repasse é assíncrono (o reembolso continua síncrono).
4. **Rede de segurança:** job que reconcilia payouts `pending` há mais de N minutos por
   `GET` (webhook pode não chegar); status desconhecido = "não confirmado", nunca sucesso.
5. **Testes:** os cenários do modo de teste (`external_reference` = `failed_insufficient_funds`,
   `failed_by_bank`, `timeout`, `internal_server_error`, `partially_processed`…) viram
   testes de contrato com respostas reais capturadas — não só mocks escritos por nós.
6. **Produção:** assinador Ed25519 (`Signature.getInstance("Ed25519")`, corpo em Base64 no
   `x-Signature`), 1ª transferência real de R$ 1 pra chave nossa pra confirmar unidade de
   `amount` e o comportamento de `PIX_CODE`.

## Ligado a

- [[mercadopago-payer-email-forbidden-sandbox]]
- [[escrow-guardas-gateway-simulado]]
- [[pagamento-confirmacao-fake]]
- [[testes-que-mentem]]
