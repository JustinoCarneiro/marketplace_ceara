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

## ✅ Implementado em 2026-06-29 (segunda rodada)

Fechou as 3 lacunas restantes:

| Recurso | Endpoints | História | Impacto |
|---|---|---|---|
| Trilha de auditoria | `GET /api/v1/admin/audit-logs` | US22/TS09 | AuditPage funcional + log imutável em 10 mutações admin |
| Transação visível | `GET /api/v1/transactions/{serviceRequestId}` | US07/M06 | Prestador/cliente vê status do escrow |
| IA de sugestão | `POST /api/v1/services/ai/suggest` | US14/M04 | AiAssistantScreen consome; fallback manual obrigatório |

Notas:
- **Auditoria**: V8 migration, entity imutável `AdminAuditLog`, hooks explícitos (abordagem sem AOP).
- **Transação**: autorização por participação no service_request (cliente_id OU prestador_id = auth); não vaza dados de terceiros.
- **IA**: stub retorna `source: "FALLBACK_MANUAL"` (IA indisponível não bloqueia). Quando provedor de IA for configurado, basta implementar `AiSuggestionServiceImpl.suggest()`.

---

## ✅ Todas as pendências de backend estão fechadas

`nearby`, `payments/webhook`, `admin/metrics`, `admin/alerts`, `admin/notifications`,
`admin/disputes(+detalhe+resolve)`, `admin/transactions`, `admin/outbox(+reprocess)`,
`admin/reports/*.csv`, `metrics.pdf`, `admin/categories`, `admin/users`, `admin/providers`,
`admin/audit-logs`, `transactions/{srId}`, `services/ai/suggest`.

