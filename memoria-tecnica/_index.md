---
tipo: indice
---

# Memória Técnica — Marketplace de Serviços Residenciais (Ceará)

Base de conhecimento viva do projeto: bugs cabeludos resolvidos (com causa raiz) e decisões técnicas
tomadas fora da spec original do [`CLAUDE.md`](../CLAUDE.md). Não documenta conceitos genéricos — só o
que é específico deste projeto e não seria óbvio olhando só o código.

Padrão da metodologia OndaDev (Memória Técnica Viva) — metodologia canônica no `onda-starter`; ponteiro local em [`docs/Metodologia_de_Desenvolvimento_-_Onda.md`](../docs/Metodologia_de_Desenvolvimento_-_Onda.md) pro
critério completo de quando vale (e quando não vale) criar uma nota aqui.

## Como usar
- **Antes de investigar um bug**, procurar em `bugs/` se algo parecido já foi resolvido.
- **Antes de tomar uma decisão de arquitetura**, procurar em `decisoes/` se já existe uma decisão
  relacionada (evita reabrir debate já resolvido ou contradizer uma decisão ativa).
- **Ao resolver um bug não-trivial** (que exigiu investigação real, causa raiz não óbvia a partir do
  código) ou **tomar uma decisão técnica fora da spec**, criar uma nota nova usando `templates/bug.md`
  ou `templates/decisao.md`, e linkar às notas relacionadas com a notação `[[nome-da-nota]]`.
- Não criar nota se o fato já tem um lar melhor (já é critério de aceite no `CLAUDE.md`, ou já tem um
  aviso dedicado em outro doc) — isso duplicaria a fonte de verdade em vez de complementá-la.

## Bugs
- [[e2e-fluxo-principal-quebrado]] — CI vermelho no master desde 13/08: DTO de proposta ganhou campo obrigatório, teste E2E não foi atualizado.
- [[jira-team-managed-endpoints-bloqueados]] — campo→layout é gap real de API; delete de issue era falta de papel atribuído, não permissão da plataforma.
- [[maestro-e2e-backend-nao-sobe]] — Maestro E2E falhava em toda run desde ~07/08: guard de canal de alerta bloqueava o boot do backend em CI.

## Decisões
*(vazio — criado retroativamente em 2026-08-03; começa a ser populado dali em diante)*
