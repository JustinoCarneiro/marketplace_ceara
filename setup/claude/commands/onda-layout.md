---
name: onda-layout
description: Fase 2b — gera o front 100% estático com dados fictícios a partir do CLAUDE.md e da identidade do projeto.
allowed-tools: Read, Write, Edit, Bash
---

# Layout & Congelamento Visual — Fase 2b

Você está na **Fase 2b** da metodologia Onda-Dev. Gere um front 100% estático, aprovável pelo cliente.

## Antes de começar

Leia obrigatoriamente:
1. `CLAUDE.md` — épicos, histórias, stack definida
2. `design/tokens.css` — identidade visual do projeto
3. `design/DESIGN.md` — guia de uso dos tokens

Se `design/tokens.css` **não existir**: pare e avise que a Fase 2a (Direção Visual) precisa ser executada primeiro no Claude (web) antes de gerar o layout.

---

## Regras de geração

### Obrigatório
- **100% estático** — sem chamadas de API reais; use dados fictícios plausíveis
- **Responsivo** — mobile-first; breakpoints conforme definido nos tokens
- **Acessibilidade AA** — contraste mínimo 4.5:1, toque ≥ 44px, `aria-label` em ações sem texto visível
- **Estados tratados** — loading, erro, vazio e sucesso para cada componente interativo
- **Navegação funcional** — todas as telas navegáveis localmente

### Hierarquia visual
- 1 ação principal (CTA) por tela
- Hierarquia tipográfica: título → subtítulo → corpo → legenda
- Nunca usar a marca da Onda em projetos de clientes — usar a identidade do cliente

### Entregável
Arquivos no diretório e framework definidos no `CLAUDE.md`. Se não definido, usar HTML/CSS/JS puro em `frontend/`.

---

## Gateway G3

Apresente o protótipo para aprovação humana.
- **Não aprovado** → liste o feedback item a item e revise
- **Aprovado** → declare **Congelamento Visual**:
  > "A partir deste ponto, qualquer mudança de layout é **mudança de escopo** e exige aditivo de prazo calculado pelo peso do módulo afetado."
