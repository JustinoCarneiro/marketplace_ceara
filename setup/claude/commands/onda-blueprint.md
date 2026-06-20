---
name: onda-blueprint
description: Fase 3 — gera ROADMAP.md com módulos pesados, contratos de API e prazo técnico calculado.
allowed-tools: Read, Write, Bash
---

# Blueprint — Fase 3

Você está na **Fase 3** da metodologia Onda-Dev. Gere a planta técnica completa antes de escrever qualquer código.

## Antes de começar

Leia obrigatoriamente:
1. `CLAUDE.md` — stack, épicos, arquitetura
2. `docs/spec.md` — histórias com critérios de aceite

---

## O que gerar

### 1. Modelagem do banco
Liste todas as entidades com campos, tipos e relacionamentos exatos.
Use formato de tabela ou diagrama textual (mermaid `erDiagram`).

### 2. Módulos independentes
Divida o sistema em módulos que podem ser desenvolvidos e testados de forma independente.
Regra: se dois módulos não podem ser construídos em paralelo, eles não são independentes — mergeie ou reordene.

### 3. Peso de cada módulo

| Peso | Dias | Critério |
|---|---|---|
| Pequeno | 1–2d | CRUD simples, baixo risco, sem integrações externas |
| Médio | 3–4d | Lógica de negócio não-trivial, integrações externas, relatórios |
| Grande | 5–7d | Gateway de pagamento, auth/permissões, máquina de estados, motor de regras |

**Regra do coração:** o módulo de maior risco entra **primeiro** na Fase 4.

### 4. Contratos de API

Para cada endpoint, defina antes de codificar:
```
[MÉTODO] /api/v1/[recurso]
Body/Params: { campo: tipo, obrigatório: bool }
Response 200: { campo: tipo }
Response 400/401/404: { error: string, code: string }
```

### 5. Rastreabilidade história ↔ módulo
Mapeie quais histórias do `spec.md` são atendidas por qual módulo (relação N:1).

### 6. Prazo técnico calculado

```
Prazo = Fase 2 (já executada) + Σ(dias dos módulos) + 2d (Fase 5)
```

Apresente a tabela de parcelas com o total em dias úteis.

---

## Gerar `ROADMAP.md`

Compile tudo no `ROADMAP.md` do projeto. Este arquivo é a **fonte única da verdade para a Fase 4**.

Estrutura mínima do ROADMAP.md:
1. Stack e decisões arquiteturais
2. Diagrama do banco
3. Lista de módulos (ordem de execução, peso, histórias atendidas)
4. Contratos de API por módulo
5. Prazo técnico calculado (tabela de parcelas)
