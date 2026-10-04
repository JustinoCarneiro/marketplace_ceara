---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# Dependabot de segurança ligado; como tratamos os alertas (e dois aceitos sem correção)

## Contexto
O repositório é público e o Dependabot estava desligado. Ao ligá-lo (2026-10-04), o GitHub listou **29 alertas
abertos (21 altos, 8 médios)**, todos em dependências npm do `admin/` e do `mobile/` — nenhum no backend (Maven).
Quase tudo é ferramenta de build/desenvolvimento lendo arquivos nossos (Vite, PostCSS, Browserslist, CLI do Expo),
não código que roda com dado de usuário. O rótulo "runtime" que o GitHub põe no `mobile/` é só porque o Expo lista
suas ferramentas em `dependencies`.

## Decisão
- **Dependabot de segurança fica ligado** (alertas + PRs de correção). Não ligamos o de versões: seria ruído.
- **PR do Dependabot só de `package-lock.json`** (subida de patch/minor de um pacote transitivo): revisão de diff +
  CI completo verde (inclui E2E do backend e os dois Playwright) e merge por squash. Quem mexe em `package.json`
  (dependência direta) ou em major sobe para revisão manual — aqui vale a regra R1 do `AGENTS.md`.
- Depois de uma leva de merges, o **Maestro roda no `master`** (o lockfile do mobile entra no build nativo e o
  workflow não dispara por mudança só de lockfile). Se falhar, reverte-se primeiro o PR de navegação/Expo.

## Dois alertas aceitos (sem versão corrigida publicada) — reavaliar a cada SDK do Expo
| Pacote | Alerta | De onde vem | Por que aceito |
|---|---|---|---|
| `braces@3.0.3` | DoS por esgotamento de pilha com chaves muito aninhadas | `expo → @expo/cli → @expo/metro-file-map → micromatch` | Só no Metro, em tempo de build, com padrões de glob da nossa própria configuração; nunca recebe entrada de usuário. |
| `node-forge@1.4.0` | verificação de assinatura RSA PKCS#1 v1.5 aceita estrutura extra | `expo-updates → @expo/code-signing-certificates` e `@expo/cli` | Ferramenta de linha de comando que gera/assina certificados na máquina de quem publica; a verificação no aparelho é código nativo, não esse pacote. |
Nenhum dos dois é importado pelo código do app (`src/`, `App.tsx`) nem entra no bundle.

## O que fica de fora
- Atualizar `react-router` do admin por conta do alerta de "modo RSC": o admin é uma SPA Vite sem modo RSC, então o
  alerta não se aplica; a subida de patch entra pela rotina normal.
- Reescrever histórico ou travar versões à mão: a rotina acima dá o mesmo resultado sem risco.

## Ligado a
- [[prestador-verificado-para-operar]] (mesma sessão: endurecimento do repositório público);
  regra do repositório público em `AGENTS.md`.
