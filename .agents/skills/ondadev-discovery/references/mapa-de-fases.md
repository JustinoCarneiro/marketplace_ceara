# Mapa de fases OndaDev → skills

Referência da skill `ondadev-discovery`. Use para se orientar entre as fases e
saber qual skill ativar em cada ponto. Antes de mudar de fase, confirme os
artefatos de entrada da fase seguinte.

| Fase | Skill canônica | Entrada | Saída / entregável | Gate |
| --- | --- | --- | --- | --- |
| 0 · Descoberta / Scaffolding | `ondadev-discovery` | Pedido aceito | Repo limpo + perfil carregado + `docs/METRICAS-PROJETO.md` | — |
| 1 · Spec Viva | `ondadev-spec` | Pedido do cliente | `CLAUDE.md` + `docs/product/spec.md` | G1 — requisitos claros? |
| 2 · Experiência verificável | `ondadev-experience` | `CLAUDE.md` | `design/tokens.css` + `DESIGN.md` + protótipo aprovado e congelado | G2 (tem identidade?) · G3 (layout aprovado?) |
| 3 · Blueprint executável | `ondadev-blueprint` | Visual congelado | `ROADMAP.md` + contratos de API + prazo técnico | — |
| 4 · Construção em pequenos lotes | `ondadev-build` | `ROADMAP.md` | Módulos testados e commitados (small releases) | G4 (testes verdes?) · G5 (pedido de mudança → Fase 1) · G6 (mais módulos?) |
| 5 · Release e operação | `ondadev-release` | Módulos completos | Deploy via CI/CD + `docs/ANALISE-PROJETO-<nome>.md` | G7 (smoke test + validação OK?) |

## Retornos previstos

- **G5 — funcionalidade nova pedida na Fase 4:** não se codifica na hora. Volta à
  Fase 1 (`ondadev-spec`), atualiza `CLAUDE.md`, atualiza testes e só então
  codifica.
- **Mudança no visual já congelado:** volta à Fase 2 (`ondadev-experience`);
  caracteriza mudança de escopo e exige aditivo de prazo pelo peso do módulo
  afetado.

## Comandos legados equivalentes (em depreciação até o PR-08)

| Skill canônica | Comandos legados substituídos |
| --- | --- |
| `ondadev-discovery` | `onda-novo`, `onda-fase`, `perfil-app`, `perfil-ecommerce`, `perfil-lp`, `perfil-sistema`, `perfil-automacao`, `onda-proposta` |
| `ondadev-spec` | `onda-spec-viva` |
| `ondadev-experience` | `onda-layout` |
| `ondadev-blueprint` | `onda-blueprint` |
| `ondadev-build` | `diretiva-primaria` |
| `ondadev-release` | — (nova; Fase 5 antes só descrita na metodologia) |
