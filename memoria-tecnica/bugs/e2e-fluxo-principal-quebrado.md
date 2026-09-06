---
tipo: bug
data: 2026-09-06
severidade: Alta
status: Resolvido
resolvido_em: 2026-09-06
---

# CI `Backend — E2E (Testcontainers)` vermelho no master desde ~13/08/2026

## Sintoma
O job `Backend — E2E (Testcontainers + PostGIS)` do `.github/workflows/ci.yml`
(`mvn -f backend/pom.xml test -Dtest="E2EFluxoPrincipalTest"`) falhava em toda
run de `master` desde 13/08/2026. Descoberto ao migrar o repo para OndaDev 3.0
(2026-09-06) — não era regressão da migração, que só mexe em docs/config.

Run de referência (PR da migração 3.0), `E2EFluxoPrincipalTest`:
`Tests run: 18, Failures: 8, Errors: 1`.

- `enviarProposta:229` → `Expected status code <201> but was <422>`, corpo
  `{ "code": "VALIDATION_ERROR" }`.
- `aceitarProposta:256` → `IllegalArgument: Unnamed path parameter cannot be null
  (path parameter at index 0 is null)` — `proposalId` ficou `null` porque o
  passo anterior (`enviarProposta`) não chegou a criar a proposta.
- `listarPropostas:244`, `webhookConfirmarPagamento:345`,
  `prestadorAvaliaCliente:442` e +4 → `1 expectation failed` cada, todas em
  cascata a partir da proposta que nunca foi criada.

Os demais jobs (Backend unit, Admin build, Admin Playwright, Mobile Playwright,
Mobile typecheck) passavam — o rot estava localizado no `E2EFluxoPrincipalTest`.

## Causa raiz
**Uma só, e não era contrato de rota.** O commit `9989772` (13/08, "feat:
agendamento na proposta (US15) + pontualidade real do prestador (US03)")
adicionou um terceiro campo **obrigatório** ao DTO de criação de proposta:

```java
// backend/.../proposal/CreateProposalRequest.java
public record CreateProposalRequest(
        @NotNull @Positive BigDecimal valor,
        @Min(1) int prazoDias,
        @NotNull @Future Instant horarioProposto   // <-- novo em 9989772
) {}
```

O `E2EFluxoPrincipalTest` (última alteração em `70b89d9`, 09/08 — anterior ao
`9989772`) continuou enviando só `{ "valor": ..., "prazoDias": ... }`. Sem
`horarioProposto`, a validação do Bean Validation devolve **422
VALIDATION_ERROR** no passo 06 do fluxo; `proposalId` nunca é preenchido e os
8 testes encadeados seguintes caem em cascata (asserção falha ou NPE de path
param `null`). O erro de "path parameter null" no `aceitarProposta` era
**efeito**, não causa — despistou a hipótese inicial de "rota mudou de
assinatura".

Nenhum outro DTO do fluxo divergiu: `CreateServiceRequestRequest` ganhou
`String bairro` no mesmo período (`70d4b58`), mas opcional — por isso o passo
`criarPedido` (Order 4/5) continuava passando e a quebra só aparecia no Order 6.

A suíte `mvn test` "rápida" não pega isso porque o `E2EFluxoPrincipalTest` só
roda no job dedicado de Testcontainers; a mensagem de commit do `9989772`
("suíte E2E real ... todos verdes") não cobriu este arquivo.

## Solução
`fix/e2e-fluxo-principal` (PR #2, merge em `master` @ `a000703`,
2026-09-06). No `enviarProposta()`:

```java
.body("""
        {
          "valor":           250.00,
          "prazoDias":       1,
          "horarioProposto": "%s"
        }
        """.formatted(Instant.now().plus(2, ChronoUnit.DAYS)))
```

`Instant.now().plus(2, DAYS)` satisfaz `@Future` de forma estável (não depende
do relógio do runner além do deslocamento de 2 dias). Sem alteração de código
de produção — só o corpo JSON do teste.

**Resultado:** CI 7/7 verde no PR #2, incluindo o job Testcontainers E2E — a
cascata inteira caiu com o único ajuste.

**Lição:** ao adicionar campo obrigatório num Request DTO, `grep` pelos testes
E2E que montam aquele corpo à mão (RestAssured `.body("""...""")`) — eles não
são atualizados pelo compilador nem pela suíte unitária rápida.

## Ligado a
- [[jira-team-managed-endpoints-bloqueados]]
