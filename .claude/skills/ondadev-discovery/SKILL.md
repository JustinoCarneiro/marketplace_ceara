---
name: ondadev-discovery
description: >-
  Descoberta OndaDev (Fase 0). Prepara um projeto novo: scaffolding a partir do
  onda-starter, escolha de um único perfil arquitetural (app, ecommerce, lp,
  sistema ou automacao) e handoff para o briefing da Fase 1. Também cobre a
  proposta comercial pré-projeto. Use ao iniciar um projeto, ao trocar de fase
  ou quando precisar carregar o contexto arquitetural de um tipo de projeto.
---

# Descoberta — Fase 0

Você está na **Fase 0** da metodologia OndaDev. O objetivo é preparar o terreno
antes de qualquer conversa de escopo: repositório limpo, perfil arquitetural
carregado e contexto enxuto.

## 1. Scaffolding do projeto

```bash
git clone https://github.com/JustinoCarneiro/onda-starter.git [nome-do-projeto]
cd [nome-do-projeto]
rm -rf .git
git init
git add .
git commit -m "chore: scaffolding inicial a partir do onda-starter"
```

Peça ao humano o nome do projeto se ainda não foi informado. Não invente nome.

## 2. Carregar UM perfil arquitetural

Carregue **apenas** a referência que corresponde ao tipo do projeto. Não leia os
outros perfis — cada um traz stack, módulos recorrentes e riscos próprios, e
misturar perfis polui o contexto.

| Tipo | Referência | Quando |
| --- | --- | --- |
| `app` | `references/perfil-app.md` | SaaS, plataforma, produto recorrente, mobile |
| `ecommerce` | `references/perfil-ecommerce.md` | Loja virtual, marketplace, catálogo com checkout |
| `lp` | `references/perfil-lp.md` | Site institucional, landing page, foco em conversão |
| `sistema` | `references/perfil-sistema.md` | Sistema interno, backoffice, múltiplos perfis de acesso |
| `automacao` | `references/perfil-automacao.md` | Integração entre sistemas, pipeline, bot, webhook |

Use o perfil como contexto arquitetural para as decisões de stack e módulos que
vierem nas fases seguintes.

## 3. Registro de métricas

Crie `docs/METRICAS-PROJETO.md` a partir do template do onda-starter e preencha
o bloco de kickoff (datas, valor e moeda do contrato, canal com comissão/saque/
tributo, valor/hora alvo). O custo/hora interno da empresa nunca entra no repo.

## 4. Handoff para a Fase 1

Com o repositório preparado e o perfil carregado, ative a skill **`ondadev-spec`**
para conduzir o briefing estruturado e gerar `CLAUDE.md` + `docs/product/spec.md`.

## Proposta comercial (pré-projeto)

Se a tarefa for gerar a proposta comercial antes do projeto começar, use
`references/proposta-comercial.md`. O módulo comercial é separado da esteira de
engenharia e não bloqueia as fases 1 a 5.

## Mapa de fases

Para orientar-se entre as fases e saber qual skill ativar em cada ponto, use
`references/mapa-de-fases.md`.

---

> **Saída esperada da Fase 0:** repositório git limpo com o onda-starter como
> base, perfil arquitetural carregado e registro de métricas iniciado.
