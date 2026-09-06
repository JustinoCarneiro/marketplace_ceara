---
name: ondadev-release
description: >-
  Release, homologação e operação OndaDev (Fase 5). Smoke test local via Docker,
  validação humana ponta a ponta, revisão final de segurança, revisão manual da
  memória técnica, análise de KPIs de fechamento e deploy via CI/CD. Use ao
  fechar o projeto, quando todos os módulos do ROADMAP.md estão concluídos.
---

# Release, homologação e operação — Fase 5

Você está na **Fase 5** da metodologia OndaDev. É a entrega oficial.

## Atividades

1. **Smoke test local** — suba o ambiente via Docker e rode toda a esteira de
   testes que realmente existe no projeto. Não invente comandos de teste.
2. **Validação humana ponta a ponta** — o humano percorre os fluxos principais.
3. **Revisão final de segurança** — rode `revisor-seguranca` nos módulos de
   risco; confirme que nenhum segredo, token ou dado restrito entrou no repo
   (ver `docs/security/data-classification.md`).
4. **Revisão manual da `memoria-tecnica/`** — a IA popula em melhor esforço, não
   por garantia. Verifique se ficou desatualizada e pode notas triviais.
5. **Análise de KPIs de fechamento** — rode o padrão da empresa
   (`docs/METRICAS-KPI.md`) usando `docs/PROMPT-ANALISE-KPI.md`, gerando
   `docs/ANALISE-PROJETO-<nome>.md`: prazo, DORA, fluxo, valor agregado,
   financeiro de serviços, SPACE e cliente. Feita perto do fim, assim que o
   escopo estabiliza; não bloqueia o deploy, mas a Fase 5 não fecha sem ela.
   Valor derivado de horas não registradas é faixa estimada, marcada como tal —
   nunca fabricar timesheet.

## Gate G7

**Smoke test + validação humana OK?**
- Não → **volta à Fase 4** (`ondadev-build`).
- Sim → **deploy via CI/CD**. Não faça push, merge, publicação ou deploy sem
  autorização humana explícita.

## Evidência por tipo de artefato

| Artefato | Evidência de pronto |
| --- | --- |
| Regra de negócio | Testes de contrato/integração verdes |
| UI | Testes de componente + acessibilidade + revisão visual |
| API | Contrato Request/Response validado |
| Banco | Migração para a frente e rollback ensaiado |
| Infraestrutura | Policy/plan revisado |
| Documentação | Lint e links válidos |

## Saída

Software em produção + `docs/ANALISE-PROJETO-<nome>.md` no histórico da empresa,
com um bloco-resumo copiado para o registro central. Alimente spec, skills e
ADRs apenas com aprendizados duráveis.
