---
name: onda-spec-viva
description: Fase 1 — conduz o briefing estruturado e gera CLAUDE.md + spec.md.
allowed-tools: Read, Write, Bash
---

# Spec Viva — Fase 1

Você está na **Fase 1** da metodologia Onda-Dev. Transforme o pedido do cliente numa especificação viva e completa, seguindo o roteiro abaixo. Não avance sem respostas suficientes em cada bloco.

## Roteiro do briefing

### Bloco 1 — Contexto
- Qual é o produto? (uma frase)
- Quem usa? (perfis de usuário e suas diferenças de permissão)
- Qual a dor central que resolve?
- Há referência de produto ou concorrente?

### Bloco 2 — Escopo e regras de negócio
- Quais funcionalidades são obrigatórias no MVP?
- Quais são desejadas mas podem ficar para depois?
- Há regras de negócio específicas (limites, cálculos, fluxos condicionais)?
- Há integrações externas (pagamento, e-mail, SMS, APIs de terceiros)?

### Bloco 3 — Volume e infraestrutura
- Estimativa de usuários simultâneos?
- Há dados sensíveis (CPF, cartão, saúde, localização)?
- Preferência de stack? (ou deixar a Onda recomendar)
- Onde vai rodar? (VPS, cloud, serverless)

### Bloco 4 — Restrições
- Há data limite?
- Há restrições de orçamento ou tecnologia?

---

## Gerar os artefatos da Fase 1

Com as respostas em mãos, gere os dois arquivos:

### `CLAUDE.md`
Preencha o template existente com:
- Stack definida
- Perfil de projeto (tipo · perfis de usuário · contexto)
- Princípios não-funcionais críticos (performance, segurança, acessibilidade, etc.)
- Épicos mapeados (lista numerada)
- Máquina de estados principal (se o produto tiver fluxo de status)

### `docs/spec.md`
Crie o arquivo com histórias de usuário e critérios de aceite:

**Formato de história:** "Como [perfil], quero [ação] para [benefício]."
**Formato de critério:** "Dado [contexto], quando [ação], então [resultado esperado]."

Regras:
- Ao menos uma história por épico
- Módulos de risco (pagamento, auth, permissões) devem ter 2+ critérios
- Histórias independentes entre si sempre que possível

---

## Gateway G1

Ao final, pergunte: **"Os requisitos estão claros o suficiente para partir para o visual?"**
- Não → identifique a pergunta específica em aberto e volte ao briefing
- Sim → declare a Fase 1 concluída e sinalize que a Fase 2 pode iniciar
