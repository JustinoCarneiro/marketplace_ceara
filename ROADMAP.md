# ROADMAP — Marketplace de Serviços Residenciais (Ceará)

> Blueprint técnico gerado na **Fase 3** da metodologia Onda-Dev (`onda-blueprint`).
> Fonte da verdade técnica: consumir **junto com** o `CLAUDE.md` e `docs/spec.md`.
> Stack: Java 21 + Spring Boot 3.x (Virtual Threads) · PostgreSQL + PostGIS · React Native/Expo.

## Mapa de fases e skills

| Fase | Skill | Entregável |
|---|---|---|
| 1 · Spec Viva | `onda-spec-viva` | `CLAUDE.md` + `docs/spec.md` ✅ |
| 2 · Layout | `onda-direcao-visual` (sem identidade) + `onda-layout` | Protótipo aprovado ⏳ |
| 3 · Blueprint | `onda-blueprint` | **Este ROADMAP + contratos** ✅ |
| 4 · XP Coding | `onda-xp-tdd` | Módulos testados e commitados ✅ M00–M12 (todos concluídos) |
| 5 · Homologação | `onda-homologacao` | Deploy em produção |

> **Decisão de prazo (Gate G2):** `design/tokens.css` **não existe** → projeto **sem identidade visual**. A Fase 2 roda **2a (direção visual) + 2b (layout) = 4 dias úteis**.

---

## 1. Modelagem de Banco de Dados

Refinamento formal do dicionário de dados de `docs/spec.md`. Tipos PostgreSQL; geolocalização em `geography(Point,4326)` para PostGIS.

### Entidades

**`users`** — base de identidade (Cliente, Prestador, Admin)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID PK | |
| nome | varchar | |
| email | varchar UNIQUE | |
| senha_hash | varchar | BCrypt/Argon2 — **nunca texto puro** (US01) |
| cpf_cifrado | bytea NULL | criptografado em repouso, LGPD (TS04) |
| role | enum | `ROLE_CLIENT` · `ROLE_PROVIDER` · `ROLE_ADMIN` |
| created_at / updated_at | timestamptz | |

**`refresh_tokens`** — sessão persistente (US12)
| id UUID PK · user_id FK→users · token_hash · expires_at · revoked bool · created_at |
- 1 user : N refresh_tokens. Rotação/revogação por hash.

**`providers_profile`** — perfil e reputação do prestador
| id UUID PK · user_id FK→users UNIQUE · categoria · bio · localizacao `geography(Point,4326)` · status_verificacao enum(`EM_VERIFICACAO`,`VERIFICADO`,`REPROVADO`) · saldo_retido numeric(12,2) · nota_media numeric(2,1) · created_at |
- Índice **GiST** em `localizacao` (TS03 — `nearby` p95 < 300ms).

**`background_checks`** — verificação assíncrona (US02)
| id · provider_id FK→providers_profile · status enum(`PENDENTE`,`APROVADO`,`REPROVADO`,`INCONCLUSIVO`) · resultado jsonb · requested_at · completed_at |

**`service_categories`** *(referência)* — `id · nome · slug UNIQUE`

**`service_requests`** — pedido / chamado (coração operacional)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID PK | |
| cliente_id | FK→users | |
| prestador_id | FK→users NULL | preenchido ao aceitar proposta |
| categoria | varchar | |
| descricao | text | |
| status | enum | `PENDENTE`→`PROPOSTO`→`ACEITO`→`EM_ANDAMENTO`→`CONCLUIDO`/`EM_DISPUTA`/`CANCELADO` |
| localizacao | geography(Point,4326) | |
| ai_descricao_sugerida | text NULL | rascunho IA (US14) |
| ai_faixa_min / ai_faixa_max | numeric NULL | orçamento sugerido (editável) |
| created_at / updated_at | timestamptz | |

**`service_media`** — multimídia (US04, TS07) · banco guarda **só a URL**
| id · service_request_id FK · tipo enum(`TEXTO`,`FOTO`,`AUDIO`) · url · created_at |

**`proposals`** — leilão simples (US15, US16)
| id · service_request_id FK · prestador_id FK→users · valor numeric(12,2) · prazo_dias int · status enum(`ATIVA`,`ACEITA`,`ENCERRADA`,`RECUSADA`) · created_at |
- Ao aceitar uma: a aceita → `ACEITA`, demais do pedido → `ENCERRADA`.

**`transactions`** — dinheiro/escrow (Épico 5)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID PK | |
| service_request_id | FK UNIQUE | 1 pedido : 1 transação |
| valor_total / valor_comissao | numeric(12,2) | split 10–25% |
| percentual_comissao | numeric(4,2) | |
| metodo | enum(`PIX`,`CARTAO`) | |
| status_pagamento | enum | `PENDENTE`→`RETIDO`→(`LIBERADO`/`REEMBOLSADO`); falha→`PENDENTE` |
| gateway_transaction_id | varchar NULL | |
| idempotency_key | varchar UNIQUE | **obrigatório** (US05, TS02) |
| created_at / updated_at | timestamptz | |

**`outbox_events`** — motor do Escrow (Saga + Outbox, TS02)
| id · agregado · agregado_id · tipo_evento · payload jsonb · status enum(`PENDENTE`,`PROCESSADO`,`FALHA`) · tentativas int · criado_em · processado_em |
- **Nunca** `@Transactional` envolvendo o gateway — estado dirigido por eventos confirmados via webhook.

**`disputes`** — mediação (US18)
| id · service_request_id FK · aberta_por FK→users · motivo text · status enum(`ABERTA`,`RESOLVIDA`) · resolucao enum(`LIBERADO`,`REEMBOLSADO`) NULL · created_at · resolved_at |

**`reviews`** — avaliação bidirecional (US08, US20)
| id · service_request_id FK · avaliador_id FK→users · avaliado_id FK→users · nota smallint(1–5) · comentario text · url_imagem NULL · created_at |
- UNIQUE `(service_request_id, avaliador_id)` · só após `CONCLUIDO` (sem retaliação).

**`sos_events`** — botão SOS (US21)
| id · service_request_id FK · user_id FK→users · lat · lng · canal_acionado · created_at | — auditável (quem/quando/onde).

**`admin_audit_log`** — auditoria de ações administrativas (US22, TS09)
| id · admin_id FK→users · acao · entidade · entidade_id · detalhe jsonb · created_at | — imutável (append-only).

> Métricas do dashboard (US23) são **derivadas por agregação** sobre `transactions`, `service_requests`, `providers_profile`, `disputes`, `sos_events` — sem tabela própria de verdade financeira (TS09). Cache/materialização é otimização opcional.

### Diagrama de relacionamentos (texto)
```
users 1─N refresh_tokens
users 1─1 providers_profile 1─N background_checks
users(cliente) 1─N service_requests N─1 users(prestador)
service_requests 1─N service_media
service_requests 1─N proposals N─1 users(prestador)
service_requests 1─1 transactions 1─N outbox_events
service_requests 1─N disputes
service_requests 1─N reviews (avaliador/avaliado → users)
service_requests 1─N sos_events N─1 users
users(admin) 1─N admin_audit_log
```

---

## 2. Modularização e Pesos

Pesos: **Pequeno (1–2d)** · **Médio (3–4d)** · **Grande (5–7d)**.

| ID | Módulo | Peso | Dias | Risco |
|---|---|---|---|---|
| M00 | **Fundação Técnica** — `@ControllerAdvice` + erros padronizados (TS06), Virtual Threads (TS01), migrations base/Flyway, observabilidade base (TS08) | Pequeno | 2 | Baixo |
| M01 | **Identidade & Auth** — cadastro Cliente, JWT curto + refresh token, roles, hash de senha | Médio | 4 | Médio |
| M02 | **Verificação de Prestador** — perfil, CPF cifrado (LGPD), background check assíncrono | Médio | 3 | Médio |
| M03 | **Descoberta & Geobusca** — `nearby` PostGIS (GiST), filtros distância/nota | Médio | 4 | Médio (SLA p95<300ms) |
| M04 | **Solicitação Multimídia + IA** — upload object storage, pedido `multipart`, IA com **fallback manual** | Grande | 6 | Alto (integração externa) |
| M05 | **Propostas & Orçamentos** — leilão simples (envio/aceite/encerramento) | Médio | 3 | Médio |
| M06 | **Pagamento & Escrow** — Pix/Cartão, Saga+Outbox+idempotência, webhook, split, disputa/reembolso | Grande | 7 | **Crítico** |
| M07 | **Execução do Serviço** — máquina de estados do chamado | Médio | 3 | Médio |
| M08 | **Avaliação Bidirecional & Reputação** — 1–5★, gating pós-`CONCLUIDO`, nota média | Pequeno | 2 | Baixo |
| M09 | **Botão SOS** — acionamento de emergência com geo + auditoria | Pequeno | 2 | Baixo |
| M10 | **Painel Admin — Métricas & Gestão** (web/React) — auth admin, dashboard de métricas, gestão de usuários, moderação de prestadores, catálogo de categorias, log de auditoria | Grande | 5 | Alto |
| M11 | **Mediação & Operações Financeiras** (web/React) — fila de disputas + resolução, visão/reconciliação de escrow, reprocessamento de outbox | Médio | 4 | **Crítico** (move dinheiro) |
| M12 | **Alertas & Relatórios Admin** — central de notificações + alertas push/e-mail (SOS, disputa, verificação inconclusiva) e exportação CSV/PDF | Médio | 3 | Médio (SOS é sensível a tempo) ✅ |
| | **Σ Total de engenharia (Fases 3+4)** | | **48** | |

---

## 3. Rastreabilidade Módulo ↔ História (N:M)

| Módulo | Histórias atendidas |
|---|---|
| M00 | TS01, TS06, TS08 (suporte transversal a todas) |
| M01 | US01, US12 · TS05 |
| M02 | US02 · TS04 |
| M03 | US03, US13 · TS03 |
| M04 | US04, US14 · TS07 |
| M05 | US15, US16 |
| M06 | US05, US06, US07, US17, US18 · TS02 |
| M07 | US19 |
| M08 | US08, US20 |
| M09 | US21 |
| M10 | US22, US23, US25, US26, US28 · TS09 |
| M11 | US24, US27 · TS02 (reusa motor do M06) |
| M12 | US29, US30 · TS04 (relatório sem CPF) · liga-se a US21/US18/US02 |

| História | Módulo(s) |
|---|---|
| US01 / US12 | M01 |
| US02 | M01 (base) + M02 |
| US03 / US13 | M03 |
| US04 / US14 | M04 |
| US15 / US16 | M05 |
| US05–US07, US17, US18 | M06 |
| US19 | M07 (orquestra M05/M06) |
| US08 / US20 | M08 |
| US21 | M09 |
| US22, US23, US25, US26, US28 | M10 |
| US24 (mediação) | M11 (consome motor escrow do M06) |
| US27 (reconciliação) | M11 |
| US29 (relatórios), US30 (alertas) | M12 |

---

## 4. Contratos API-First (`/api/v1`)

JSON · DTOs = **Records** (nunca expõem entidades, TS04) · erros padronizados via `@ControllerAdvice` (400/404/422).

### ⚠️ Auditoria de drift — contrato projetado vs. implementado (2026-06-28)

Os contratos abaixo são o **desenho API-First da Fase 3**. A implementação adotou convenções diferentes — **a fonte da verdade é o código**. Convenção real de URL: recurso é `/api/v1/service-requests` (kebab-case, top-level), **não** `/api/v1/services/requests`.

| Projetado (nas seções abaixo) | Implementado (real) |
|---|---|
| `POST /api/v1/providers` | `POST /api/v1/auth/register/provider` |
| `POST /api/v1/services/requests` | `POST /api/v1/service-requests` (+ `POST .../{id}/media`) |
| `POST .../{id}/proposals` · accept `POST` | `POST`/`GET /api/v1/service-requests/{id}/proposals` · accept/reject = **`PUT`** `/api/v1/proposals/{id}/accept\|reject` |
| `POST /api/v1/payments` (body serviceRequestId) | `POST /api/v1/service-requests/{id}/payment` · header **`X-Idempotency-Key`** |
| `POST .../{id}/disputes` (abrir) | `POST /api/v1/service-requests/{id}/dispute` |
| `.../complete` + `.../confirm` | `POST /api/v1/service-requests/{id}/confirm-completion` |
| `POST .../{id}/reviews` | `POST /api/v1/service-requests/{id}/review` |
| `POST .../{id}/sos` | `POST /api/v1/sos` + `PATCH /api/v1/sos/{id}/resolve` (top-level) |
| `POST /api/v1/admin/providers/{id}/verify` | `POST /api/v1/admin/providers/{userId}/moderate` |
| `POST /api/v1/disputes/{id}/resolve` | `POST /api/v1/admin/disputes/{serviceRequestId}/resolve` |

**Implementado em 2026-06-29** (fechando parte do drift; rotas reais que o front já consumia):
- `GET/POST/PATCH /api/v1/admin/categories` (US28) — CRUD do catálogo (`category` pkg).
- `GET /api/v1/admin/users` + `POST .../{id}/suspend|reactivate` (US26) — admin nunca é suspenso.
- `GET /api/v1/admin/providers` + `POST .../{userId}/verify|reject` (US25) — lista + atalhos de moderação.
- `POST /api/v1/admin/notifications/mark-all-read` (US30).

**Pendências de backend ainda em aberto** (detalhe em `docs/PENDENCIAS_BACKEND.md`):
- `POST /api/v1/services/ai/suggest` (US14/M04) — endpoint de IA com fallback manual obrigatório.
- `GET /api/v1/transactions/{id}` (US07/M06) — visão da transação retida pelo prestador.
- `GET /api/v1/admin/audit-logs` + tabela `admin_audit_log` (US22/TS09) — trilha de auditoria.

Alinhados ao projetado: `nearby`, `payments/webhook`, `admin/metrics`, `admin/alerts`, `admin/notifications`, `admin/disputes`, `admin/transactions`, `admin/outbox(+reprocess)`, `admin/reports/*.csv|metrics.pdf`.

### M01 — Identidade & Auth
```
POST /api/v1/auth/register/client
  req:  { nome, email, senha }
  201:  { accessToken, refreshToken, role: "ROLE_CLIENT" }
  422:  { code:"EMAIL_IN_USE", message }

POST /api/v1/auth/login           req:{ email, senha } → 200 { accessToken, refreshToken, role }
POST /api/v1/auth/refresh         req:{ refreshToken } → 200 { accessToken, refreshToken } | 401
```

### M02 — Verificação de Prestador
```
POST /api/v1/providers            (ROLE_PROVIDER)
  req:  { nome, email, senha, cpf, categoria, bio, lat, lng }
  201:  { id, statusVerificacao:"EM_VERIFICACAO" }
GET  /api/v1/providers/{id}       200 { id, nome, categoria, statusVerificacao, notaMedia }
# callback interno do background check muda status → VERIFICADO|REPROVADO (assíncrono)
```

### M03 — Descoberta & Geobusca
```
GET /api/v1/providers/nearby?lat=&lng=&categoria=&raioKm=&notaMin=
  200: [ { id, nome, categoria, notaMedia, distanciaKm } ]   # ordenado por distância (PostGIS)
  200: []                                                     # raio vazio = estado tratado, não erro
  SLA: p95 < 300ms
```

### M04 — Solicitação Multimídia + IA
```
POST /api/v1/services/requests    Content-Type: multipart/form-data   (ROLE_CLIENT)
  parts: descricao?, categoria, lat, lng, media[] (foto/audio)
  201:   { id, status:"PENDENTE", media:[{tipo,url}] }

POST /api/v1/services/ai/suggest  req:{ descricao?, mediaUrls[] }
  200:  { descricaoSugerida, faixaMin, faixaMax, source:"AI" }
  200:  { descricaoSugerida:null, source:"FALLBACK_MANUAL" }   # IA indisponível NÃO bloqueia
```

### M05 — Propostas & Orçamentos
```
POST /api/v1/services/requests/{id}/proposals   (ROLE_PROVIDER)
  req:  { valor, prazoDias }
  201:  { id, status:"ATIVA" }  → pedido vai a PROPOSTO
  422:  { code:"REQUEST_UNAVAILABLE" }           # já aceito por outro
GET  /api/v1/services/requests/{id}/proposals   200 [ { id, prestador, valor, prazoDias, status } ]
POST /api/v1/proposals/{id}/accept  (ROLE_CLIENT) 200 { aceita:id, encerradas:[...] }
```

### M06 — Pagamento & Escrow  *(módulo crítico)*
```
POST /api/v1/payments              (ROLE_CLIENT)   Header: Idempotency-Key (obrigatório)
  req:  { serviceRequestId, metodo:"PIX"|"CARTAO" }
  202:  { transactionId, statusPagamento:"PENDENTE", checkout:{...} }
  # confirmação só por webhook → RETIDO; pedido → ACEITO

POST /api/v1/payments/webhook      (gateway → plataforma)   # idempotente, dirige o estado
GET  /api/v1/transactions/{serviceRequestId}   200 { statusPagamento, valorTotal, valorComissao }
POST /api/v1/services/requests/{id}/disputes   req:{ motivo } → 200 { status:"EM_DISPUTA" }
POST /api/v1/disputes/{id}/resolve (ROLE_ADMIN) req:{ resolucao:"LIBERADO"|"REEMBOLSADO" }
# Conclusão confirmada → Outbox dispara split → transaction LIBERADO. SEM @Transactional sobre o gateway.
```

### M07 — Execução do Serviço
```
POST /api/v1/services/requests/{id}/start     (ROLE_PROVIDER)  ACEITO → EM_ANDAMENTO
POST /api/v1/services/requests/{id}/complete  (ROLE_PROVIDER)  → aguarda confirmação cliente
POST /api/v1/services/requests/{id}/confirm   (ROLE_CLIENT)    EM_ANDAMENTO → CONCLUIDO
POST /api/v1/services/requests/{id}/cancel    → CANCELADO (reembolso se houver retido)
  422: { code:"INVALID_TRANSITION" }   # ex.: PENDENTE → CONCLUIDO
```

### M08 — Avaliação & Reputação
```
POST /api/v1/services/requests/{id}/reviews   (CLIENT|PROVIDER)
  req: { nota:1..5, comentario, urlImagem? }
  201: { id }   → recalcula nota_media do avaliado
  422: { code:"REQUEST_NOT_COMPLETED" }
```

### M09 — Botão SOS
```
POST /api/v1/services/requests/{id}/sos   (usuário em atendimento)
  req: { lat, lng }
  201: { id, canalAcionado, registradoEm }   # exige pedido EM_ANDAMENTO; auditável
```

### M10 — Painel Admin: Métricas & Gestão  *(web · todas exigem `ROLE_ADMIN` → 403 caso contrário)*
```
GET  /api/v1/admin/metrics?de=&ate=&bairro=
  200: { gmv, receitaComissao, ticketMedio, pedidosPorStatus:{...},
         taxaConclusao, disputasAbertas, tempoMedioResolucaoH,
         prestadoresVerificados, prestadoresAtivos, clientesAtivos, sosAcionados }

GET  /api/v1/admin/users?q=&role=&status=        200 [ { id, nome, email, role, status } ]
POST /api/v1/admin/users/{id}/suspend            200 { status:"SUSPENSO" }   # auditado
POST /api/v1/admin/users/{id}/reactivate         200 { status:"ATIVO" }

GET  /api/v1/admin/providers?statusVerificacao=  200 [ { id, nome, statusVerificacao } ]
POST /api/v1/admin/providers/{id}/verify         req:{ decisao:"VERIFICADO"|"REPROVADO", justificativa } → 200

GET  /api/v1/admin/categories                    200 [ { id, nome, slug, ativa } ]
POST /api/v1/admin/categories                    req:{ nome, slug } → 201
PATCH/api/v1/admin/categories/{id}               req:{ nome?, ativa? } → 200

GET  /api/v1/admin/audit?entidade=&adminId=      200 [ { adminId, acao, entidade, entidadeId, criadoEm } ]
```

### M11 — Mediação & Operações Financeiras  *(web · `ROLE_ADMIN` · move dinheiro pelo motor do M06)*
```
GET  /api/v1/admin/disputes?status=ABERTA        200 [ { id, serviceRequestId, abertaPor, motivo, valorRetido } ]
GET  /api/v1/admin/disputes/{id}                 200 { ...histórico, partes, midias, transaction }
POST /api/v1/disputes/{id}/resolve               (já em M06) req:{ resolucao:"LIBERADO"|"REEMBOLSADO" }
  # dispara Saga/Outbox → transaction LIBERADO|REEMBOLSADO; pedido CONCLUIDO|CANCELADO. Idempotente.

GET  /api/v1/admin/transactions?status=          200 [ { id, serviceRequestId, valorTotal, statusPagamento } ]
GET  /api/v1/admin/outbox?status=FALHA           200 [ { id, agregado, tipoEvento, tentativas } ]
POST /api/v1/admin/outbox/{id}/reprocess         202   # reenfileira idempotente (sem duplicar cobrança/repasse)
```

### M12 — Alertas & Relatórios Admin  *(web · `ROLE_ADMIN`)*
```
GET  /api/v1/admin/notifications?lida=           200 [ { id, tipo:"SOS"|"DISPUTA"|"VERIFICACAO", refId, criadoEm, lida } ]
POST /api/v1/admin/notifications/{id}/read       200
# alertas SOS/DISPUTA também disparam push/e-mail fora do painel (garantido p/ SOS)

GET  /api/v1/admin/reports/metrics.pdf?de=&ate=&bairro=     200  application/pdf   # resumo de métricas
GET  /api/v1/admin/reports/{recurso}.csv?...               200  text/csv          # recurso: transactions|disputes|requests
# relatórios NUNCA expõem CPF (TS04/LGPD)
```

### Envelope de erro padrão (`@ControllerAdvice`, TS06)
```
{ "timestamp", "status", "code", "message", "path" }
```

---

## 5. Ordem de execução (Kanban)

Dependências respeitadas; itens na mesma linha podem paralelizar.

```
M00 Fundação
   └─► M01 Identidade & Auth
          ├─► M02 Verificação de Prestador ─┐
          └─► M03 Geobusca ────────────────┤
                                            ▼
                                      M04 Solicitação + IA
                                            ▼
                                      M05 Propostas
                                            ▼
                                      M06 Pagamento & Escrow   ◄── caminho crítico
                                            ▼
                                      M07 Execução (máquina de estados)
                                            ▼
                              ┌── M08 Avaliação ──┬── M09 SOS ──┐  (paralelos no fim)
                                            ▼
                              M10 Painel Admin (Métricas & Gestão)  ─┐ web/React
                              M11 Mediação & Ops Financeiras ────────┤ (após M06)
                              M12 Alertas & Relatórios ──────────────┘ (sobre M10/M11)
```

> **M11 depende do M06** (reusa o motor Saga/Outbox para resolver disputa). **M10** depende dos dados dos demais módulos para as métricas — por isso o bloco admin entra ao final, mas o painel web pode começar a ser estruturado em paralelo assim que o M06 estabiliza.

---

## 6. Prazo Técnico Calculado

Fórmula (metodologia Onda): `Fase 2 + Σ(pesos Fases 3 e 4) + 2d (Fase 5)`

| Parcela | Dias úteis |
|---|---|
| Fase 2 — **sem identidade** (2a direção visual + 2b layout) | 4 |
| Fases 3 + 4 — Σ dos 13 módulos (M00–M12) | 48 |
| Fase 5 — Homologação/Deploy | 2 |
| **Prazo técnico blindado** | **54 dias úteis** |

> O caminho crítico é **M06 (Pagamento & Escrow, Grande/7d)**, do qual o **M11 (Mediação)** depende. O bloco admin (M10+M11+M12 = 12d) foi incorporado com o Épico 9 — evolução do prazo: **42d** (sem admin) → **51d** (admin núcleo) → **54d** (com alertas + relatórios). Qualquer mudança no visual já congelado (retorno à Fase 2) ou novo escopo entra como módulo com o mesmo peso e **adiciona** ao prazo de forma consistente.

---

## Ponteiros
- Histórias e critérios BDD: `docs/spec.md`
- Regras de negócio e princípios: `CLAUDE.md`
- Identidade visual (a gerar na Fase 2): `design/tokens.css` + `design/DESIGN.md`
