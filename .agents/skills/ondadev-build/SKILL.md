---
name: ondadev-build
description: >-
  Construção em pequenos lotes OndaDev (Fase 4). Esteira XP com ciclo TDD
  Red-Green-Refactor módulo a módulo, diretiva primária de não alterar código
  existente sem um teste que justifique a quebra, revisão de segurança nos
  módulos de risco, small commits e atualização de status no ROADMAP.md. Use
  durante a codificação, consumindo o ROADMAP.md.
---

# Construção em pequenos lotes — Fase 4

Você está na **Fase 4** da metodologia OndaDev. Fluxo contínuo (Kanban)
consumindo o `ROADMAP.md`.

## Diretiva primária (abertura da fase)

Leia atentamente o `CLAUDE.md` e o `ROADMAP.md` do projeto. A partir de agora,
você **não tem permissão** para alterar a sintaxe ou o comportamento de código
já existente sem aplicar rigorosamente o ciclo TDD. Toda alteração passa
primeiro por um teste escrito que justifique a quebra. Texto completo em
`references/diretiva-primaria.md`.

## Ciclo TDD por módulo

Puxe o módulo de maior risco primeiro (regra do coração). Para cada módulo:

1. **Red** — escreva os testes com mocks; eles falham.
2. **Green** — só o código necessário para passar.
3. **Refactor** — DRY e otimização sem quebrar os testes.
4. **Segurança** — nos módulos de risco (pagamento, auth, permissões, dados
   sensíveis), rode o agente `revisor-seguranca`. Para logs de erro pesados,
   isole a investigação no agente `testador-tdd`.
5. **Commit limpo** (small release).
6. **Atualize o status do módulo no `ROADMAP.md`** de `⬜ Pendente` para
   `✅ Concluído (AAAA-MM-DD)` — opcionalmente com contagem de testes. Use
   `🔄 Em andamento` se o módulo passar de uma sessão. Sempre esses três
   marcadores exatos, sem variações.

## Memória técnica

Antes de investigar um bug não-trivial ou decidir algo fora da spec, consulte
`memoria-tecnica/`. Ao resolver algo cuja causa não era óbvia, registre uma nota
com o template em `memoria-tecnica/templates/`.

## Coleta de métricas (leve)

Ao fim de cada sessão, uma linha de timesheet em `docs/METRICAS-PROJETO.md`. Ao
parar por causa externa (cliente, terceiro, infra), abra e feche um episódio no
log de espera/impedimento do mesmo arquivo.

## Gates

- **G4 — testes verdes?** Não → volta ao ciclo TDD.
- **G5 — pedido de mudança?** Sim → **volta à Fase 1** (`ondadev-spec`): não se
  codifica na hora; atualiza `CLAUDE.md`, atualiza os testes e só então codifica.
- **G6 — mais módulos na fila?** Sim → puxa o próximo · Não → ative
  `ondadev-release`.
