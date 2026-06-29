# 🧩 Pendências de Backend — Lacunas frontend ↔ backend

> **Origem:** Auditoria de drift em 2026-06-28/29 (ROADMAP §4). O painel admin (React)
> chamava endpoints que não existiam no backend, degradando para lista vazia / ação
> silenciosa (`catch { setX([]) }`) — telas presentes, porém inoperantes.
> **Convenção:** a fonte da verdade é o código; rotas reais em `ROADMAP.md` §4.

---

## ✅ Implementado em 2026-06-29

Fechou as lacunas que travavam 4 telas do admin:

| Recurso | Endpoints | História | Telas destravadas |
|---|---|---|---|
| Catálogo de categorias | `GET/POST/PATCH /api/v1/admin/categories` | US28 | CategoriesPage |
| Gestão de usuários | `GET /api/v1/admin/users` · `POST .../{id}/suspend\|reactivate` | US26 | UsersPage |
| Lista + moderação de prestadores | `GET /api/v1/admin/providers` · `POST .../{userId}/verify\|reject` | US25 | ProvidersPage |
| Marcar todas notificações lidas | `POST /api/v1/admin/notifications/mark-all-read` | US30 | NotificationsPage |

Notas de implementação:
- Pacote novo `com.onda.marketplace.category` (entity/repo/service/DTOs + `CategoryAdminController`).
- Usuários: suspensão usa o flag `users.ativo` (sem migration); **admin nunca é suspenso**.
- Prestadores: `verify`/`reject` são atalhos para `ModerationService` (APROVAR/REPROVAR); o `id` do DTO é o **userId** (o que as ações esperam). Lista via fetch join do usuário.
- Cobertura: `CategoryServiceTest`, `UserAdminServiceTest`, `ProviderAdminServiceTest`, `+marcarTodasLidas` em `NotificationServiceTest`, `+6` casos em `AdminControllerTest`. Todos verdes.

---

## 🔴 Pendências em aberto (priorizadas)

### 1. Trilha de auditoria — `admin_audit_log` (US22 / TS09) · Médio
**Estado:** sem tabela, sem entidade, sem endpoint. O front (`AuditPage`) chama
`GET /api/v1/admin/audit-logs` → hoje retorna vazio.
**O que falta:**
- Migration `V8__admin_audit_log.sql` (id, admin_id, acao, entidade, entidade_id, detalhe jsonb, criado_em) — **append-only**.
- Entity + repository + serviço de escrita.
- Registrar evento em **toda ação administrativa** (resolver disputa, moderar prestador, suspender usuário, editar categoria…). Idealmente via aspecto/interceptor para não poluir cada service.
- `GET /api/v1/admin/audit-logs` (lista paginada, filtros por entidade/admin).
> Requisito não-funcional crítico (TS09): log **imutável** (quem/o quê/quando).

### 2. IA de sugestão de pedido — `POST /api/v1/services/ai/suggest` (US14 / M04) · Médio
**Estado:** sem endpoint. O app mobile (`AiAssistantScreen`) abre pedido só no fluxo manual.
**O que falta:**
- Endpoint que recebe `{ descricao?, mediaUrls[] }` e devolve `{ descricaoSugerida, faixaMin, faixaMax, source:"AI" }`.
- **Fallback manual obrigatório** (princípio do CLAUDE.md): se a IA falhar/indisponível, responder `{ source:"FALLBACK_MANUAL" }` sem bloquear — IA nunca é caminho crítico.
- Integração com o provedor de IA (definir; manter chave fora do repo).

### 3. Transação retida visível ao prestador — `GET /api/v1/transactions/{id}` (US07 / M06) · Pequeno
**Estado:** existe só `GET /api/v1/admin/transactions` (admin). O prestador não tem GET próprio.
**O que falta:**
- Endpoint autorizado ao prestador/cliente do pedido: `{ statusPagamento, valorTotal, valorComissao }`.
- Checar autorização por participação no `service_request` (não vazar transações de terceiros).

---

## Itens já alinhados (sem ação)
`nearby`, `payments/webhook`, `admin/metrics`, `admin/alerts`, `admin/notifications`,
`admin/disputes(+detalhe+resolve)`, `admin/transactions`, `admin/outbox(+reprocess)`,
`admin/reports/*.csv` e `metrics.pdf`.
