---
tipo: bug
data: 2026-09-06
severidade: Alta
status: Aberto
---

# CI `Backend — E2E (Testcontainers)` vermelho no master desde ~13/08/2026

## Sintoma
O job `Backend — E2E (Testcontainers + PostGIS)` do `.github/workflows/ci.yml`
(`mvn -f backend/pom.xml test -Dtest="E2EFluxoPrincipalTest"`) falha em toda run
de `master` desde pelo menos 13/08/2026. Descoberto ao migrar o repo para
OndaDev 3.0 (2026-09-06) — não é regressão da migração, que só mexe em
docs/config.

Run de referência (PR da migração 3.0), `E2EFluxoPrincipalTest`:
`Tests run: 18, Failures: 8, Errors: 1`.

- `aceitarProposta:256` → `IllegalArgument: Unnamed path parameter cannot be null
  (path parameter at index 0 is null)` — provável endpoint/rota que mudou de
  assinatura e o teste E2E não acompanhou (id null na montagem da URL).
- `listarPropostas:244`, `webhookConfirmarPagamento:345`,
  `prestadorAvaliaCliente:442` → `1 expectation failed` cada — asserções de
  status/corpo que não batem mais.
- +4 outras falhas de asserção na mesma suíte.

Os demais jobs (Backend unit, Admin build, **Admin Playwright**, **Mobile
Playwright**, Mobile typecheck) passam — o rot está localizado no
`E2EFluxoPrincipalTest`, não na infra de teste.

## Causa raiz
Não investigada a fundo. Hipótese: o `E2EFluxoPrincipalTest` ficou dessincronizado
do backend depois de mudanças no fluxo de proposta/aceite/pagamento (o erro de
"path parameter null" no `aceitarProposta` aponta para contrato de rota alterado).
Precisa de uma sessão dedicada de debug do backend (rodar
`mvn -f backend/pom.xml test -Dtest=E2EFluxoPrincipalTest` local contra o
Testcontainers e corrigir suíte + eventual regressão real).

## Solução
Pendente. Enquanto não resolvido: **a CI de `master` está vermelha por este
motivo conhecido** — ao avaliar uma nova PR, comparar contra este baseline e não
assumir que qualquer vermelho é regressão nova.

## Ligado a
- [[jira-team-managed-endpoints-bloqueados]]
