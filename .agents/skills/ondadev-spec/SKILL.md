---
name: ondadev-spec
description: >-
  Spec Viva OndaDev (Fase 1). Conduz o briefing estruturado que destrava o
  escopo e gera CLAUDE.md + docs/product/spec.md com épicos, histórias de
  usuário e critérios de aceite no formato Dado/Quando/Então. Use quando os
  requisitos ainda estão vagos, quando falta a spec do projeto ou quando um
  pedido de mudança exige voltar à Fase 1.
---

# Spec Viva — Fase 1

Você está na **Fase 1** da metodologia OndaDev. Transforme o pedido do cliente
(muitas vezes uma única frase) numa especificação viva e completa. Não avance
sem respostas suficientes em cada bloco.

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

## Gerar os artefatos da Fase 1

### `CLAUDE.md`
Preencha o template existente com:
- Stack definida
- Perfil de projeto (tipo · perfis de usuário · contexto)
- Princípios não-funcionais críticos (performance, segurança, acessibilidade)
- Épicos mapeados (lista numerada)
- Máquina de estados principal (se o produto tiver fluxo de status)

### `docs/product/spec.md`
Histórias de usuário e critérios de aceite:

- **História:** "Como [perfil], quero [ação] para [benefício]."
- **Critério:** "Dado [contexto], quando [ação], então [resultado esperado]."

Regras:
- Ao menos uma história por épico
- Módulos de risco (pagamento, auth, permissões) têm 2+ critérios
- Histórias independentes entre si sempre que possível
- Classifique os dados conforme `docs/security/data-classification.md`

## Gate G1

Ao final, pergunte: **"Os requisitos estão claros o suficiente para partir para
a experiência?"**
- Não → identifique a pergunta específica em aberto e volte ao briefing
- Sim → declare a Fase 1 concluída e ative `ondadev-experience`
