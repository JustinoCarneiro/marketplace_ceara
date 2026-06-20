---
name: onda-novo
description: Fase 0 — scaffolding de projeto novo. Clona onda-starter, carrega o perfil arquitetural e inicia o briefing da Fase 1.
argument-hint: ecommerce | app | lp | sistema | automacao
allowed-tools: Bash, Write, Read
---

# Fase 0 — Scaffolding

Perfil solicitado: **$ARGUMENTS**

## Passo 1 — Clone do template

Execute:
```bash
git clone https://github.com/JustinoCarneiro/onda-starter.git [nome-do-projeto]
cd [nome-do-projeto]
rm -rf .git
git init
git add .
git commit -m "chore: scaffolding inicial a partir do onda-starter"
```

Peça ao humano o nome do projeto se ainda não foi informado.

## Passo 2 — Carregar o perfil arquitetural

Leia o arquivo de perfil correspondente ao argumento:

| Argumento | Perfil |
|---|---|
| `ecommerce` | `/perfil-ecommerce` — loja virtual, marketplace |
| `app` | `/perfil-app` — SaaS, plataforma, produto recorrente |
| `lp` | `/perfil-lp` — site institucional, landing page |
| `sistema` | `/perfil-sistema` — sistema interno, backoffice |
| `automacao` | `/perfil-automacao` — pipeline, bot, integração |

Use o perfil como contexto arquitetural para todas as decisões de stack e módulos que vierem a seguir.

## Passo 3 — Iniciar a Fase 1

Com o repositório preparado e o perfil carregado, execute `/onda-spec-viva` para iniciar o briefing estruturado e gerar o `CLAUDE.md` + `docs/spec.md`.

---

> **Saída esperada da Fase 0:** repositório git limpo, com o template onda-starter como base, pronto para receber os artefatos da Fase 1.
