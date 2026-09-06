---
name: ondadev-experience
description: >-
  Experiência verificável OndaDev (Fase 2). Direção visual quando o cliente não
  tem identidade (gera design/tokens.css + DESIGN.md a partir de um starter
  neutro, nunca da marca da Onda) e protótipo estático aprovável, com
  acessibilidade AA, hierarquia clara e estados de carregamento/erro/vazio. Use
  para gerar ou congelar o layout antes do blueprint técnico.
---

# Experiência verificável — Fase 2

Você está na **Fase 2** da metodologia OndaDev. O objetivo é um protótipo
estático aprovável pelo cliente, que mitiga o risco de mudança de fluxo depois
que o banco já existe.

## Antes de começar

Leia obrigatoriamente:
1. `CLAUDE.md` — épicos, histórias, stack definida
2. `design/tokens.css` — identidade visual do projeto
3. `design/DESIGN.md` — guia de uso dos tokens

## Gate G2 — o cliente tem identidade visual?

- **Não → Fase 2a (Direção Visual):** briefing de marca curto (3–5 adjetivos,
  referências que gosta/detesta) → 2–3 direções divergentes em style tiles →
  o humano escolhe → refino → emitir `design/tokens.css` + `design/DESIGN.md`.
  Parta de um starter neutro e acessível, **nunca da marca da Onda**.
- **Sim → vá direto para a Fase 2b.**

Se `design/tokens.css` não existir e o cliente já tem identidade, colete os
tokens antes de gerar o layout.

## Fase 2b — Layout

### Obrigatório
- **100% estático** — sem chamadas de API reais; dados fictícios plausíveis
- **Responsivo** — mobile-first; breakpoints conforme os tokens
- **Acessibilidade AA** — contraste ≥ 4.5:1, alvo de toque ≥ 44px, `aria-label`
  em ações sem texto visível
- **Estados tratados** — loading, erro, vazio e sucesso em cada componente interativo
- **Navegação funcional** — todas as telas navegáveis localmente

### Hierarquia visual
- 1 ação principal (CTA) por tela
- Hierarquia tipográfica: título → subtítulo → corpo → legenda
- Nunca usar a marca da Onda em projeto de cliente — usar a identidade do cliente

### Entregável
Arquivos no diretório e framework definidos no `CLAUDE.md`. Se não definido,
HTML/CSS/JS puro em `frontend/`.

## Gate G3 — layout aprovado?

Apresente o protótipo para aprovação humana.
- **Não aprovado** → liste o feedback item a item e revise
- **Aprovado** → declare **Congelamento Visual**:
  > "A partir deste ponto, qualquer mudança de layout é mudança de escopo e
  > exige aditivo de prazo calculado pelo peso do módulo afetado."

Em seguida, ative `ondadev-blueprint`.
