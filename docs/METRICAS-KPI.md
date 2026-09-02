# Métricas e KPIs de Projeto — Padrão da Empresa

> **Status:** adotado. É o padrão referenciado pela **Fase 5 da metodologia Onda**
> (`Metodologia_de_Desenvolvimento_-_Onda.md`, seção 14 — "Análise de KPIs de Fechamento").
> Semeado a partir do projeto Heliene Araújo (set/2026). Deve migrar para `onda-starter/setup/`
> junto com o resto do ecossistema de skills.
> **Prompt operacional (para rodar a análise em qualquer projeto):** [`./PROMPT-ANALISE-KPI.md`](./PROMPT-ANALISE-KPI.md).
> **Exemplo de análise aplicada:** `docs/ANALISE-PROJETO-HELIENE.md` no repositório do projeto Heliene Araújo (piloto).

---

## 1. O registro de métricas do projeto — `docs/METRICAS-PROJETO.md`

Cada projeto tem um arquivo `docs/METRICAS-PROJETO.md` (template no `onda-starter`), criado na
Fase 0 e mantido durante a Fase 4. Sem ele, mais da metade do catálogo abaixo não fecha (não dá
pra calcular margem real, flow efficiency, CPI, realization rate). Tem três blocos:

### 1.1 Kickoff (Fase 0)
Nome/slug · valor do contrato + moeda · canal (Workana/direto) com comissão %, taxa de saque % e
regime tributário · as 4 datas (aceite da proposta, início do prazo, limite contratual, entrega) ·
valor/hora alvo · peso dos módulos (copiado do `ROADMAP.md` na Fase 3).
O **custo/hora interno da empresa** é o único número sensível — fica no sibling privado
(`<projeto>-docs-privados/`) ou é informado na hora da análise, **nunca no repo**.

### 1.2 Timesheet (Fase 4)
Uma linha por sessão: `data | fase | horas | nota`. Fase = as 6 da metodologia (0–5). Alimenta
`effective hourly rate`, `project gross margin`, `CPI` e `estimation accuracy`.

### 1.3 Log de espera / impedimento (Fase 4)
Um episódio sempre que o trabalho **parar por causa externa**:
`início | fim | motivo (cliente/terceiro/técnico/interno) | o que destrava`.
No board: uma coluna **"Aguardando cliente / Impedido"** com carimbo de data-hora na entrada e na
saída. Alimenta `flow efficiency`, `blocked time %` e a estatística de causa de bloqueio
(argumento para cláusula de conteúdo em contrato).

---

## 2. Conjunto mínimo obrigatório (~12 KPIs)

Medir tudo num projeto de baixo valor não se paga. Este subconjunto cobre planejamento e finanças
sem virar burocracia. Preencher no **fechamento** de todo projeto.

| # | KPI | De onde sai | Alerta |
|---|---|---|---|
| 1 | 4 datas: aceite · início do prazo · entrega · limite contratual | kickoff (1.1) | — |
| 2 | Lead time · cycle time · flow efficiency | log de espera (1.3) | flow efficiency < 20% |
| 3 | On-time delivery (sim/não) + folga em dias | datas (1.1) | folga < 10% do prazo |
| 4 | Horas por fase | timesheet (1.2) | — |
| 5 | Effective hourly rate (bruto e líquido) | receita ÷ horas | abaixo do valor/hora alvo |
| 6 | Project gross margin (com custo de mão de obra) | líquido − horas × custo/hora interno | < 45% |
| 7 | Receita por módulo / história | valor ÷ escopo do ROADMAP | calibra a próxima proposta |
| 8 | Mudanças de escopo: nº · absorvidas vs. faturadas | changelog de escopo do `CLAUDE.md` | mudança > 0 e aditivo = R$ 0 |
| 9 | Estimation accuracy: dias de peso (Fase 3) vs. lead time real | ROADMAP vs. datas | desvio > 50% |
| 10 | Escaped defects + incidentes de produção (nº · severidade · recovery time) | `memoria-tecnica/` + logs | qualquer Sev-alta |
| 11 | Rework rate (% commits `fix:` · rodadas de revisão do cliente) | git + registro de revisões | > 30% |
| 12 | CSAT do cliente | 1 pergunta pós-entrega ("0–10, recomendaria?") | < 8 |

---

## 3. Catálogo completo por framework

### 3.1 Entrega de software — DORA (redefinido em 2024)

| Métrica | O que mede | Grupo | Faixa de elite |
|---|---|---|---|
| Deployment frequency | Frequência de deploys em produção | throughput | sob demanda / diária |
| Lead time for changes | `commit` → deploy em produção | throughput | < 1 dia |
| Change failure rate | % de deploys que causam degradação (hotfix/rollback) | estabilidade | 0–15% |
| Failed deployment recovery time (ex-"MTTR") | Tempo p/ restaurar serviço após falha **causada por uma mudança** | estabilidade | < 1 h |
| Deployment rework rate (novo em 2024) | % de deploys que são trabalho não planejado p/ corrigir bug | retrabalho | baixo |

### 3.2 Fluxo — Flow / Kanban

| Métrica | Definição | Fórmula / uso |
|---|---|---|
| Lead time | Entrada no backlog → entregue (inclui espera) | data_entrega − data_entrada |
| Cycle time | Início do trabalho ativo → pronto (só execução) | data_pronto − data_início_ativo |
| **Flow efficiency** | Quanto do lead time foi trabalho de valor | tempo_ativo ÷ (ativo + espera) — 15–40% comum, > 40% bom |
| Wait time | Atraso previsível (fila, aguardando revisão) | Σ dias em colunas de fila |
| Blocked time | Atraso **inesperado** (dependência de cliente/terceiro/infra) | Σ dias em "Impedido" |
| Throughput | Itens concluídos por período | contagem / semana → base de previsão |
| WIP | Itens começados e não terminados | limitar WIP encurta o lead time |
| Lei de Little | Relação entre os três | lead time ≈ WIP ÷ throughput |
| Aging WIP | Idade do item em progresso mais antigo | alerta precoce; < cycle time típico |
| Flow distribution | Proporção do esforço entre feature / bug / débito / risco | % por tipo ao longo do tempo |

### 3.3 Planejamento — métricas ágeis

| Métrica | Definição | Cuidado |
|---|---|---|
| Velocity | Itens/pontos concluídos por ciclo | só p/ forecast do próprio time; nunca meta nem comparação |
| Commitment accuracy (say-do) | % do comprometido que foi entregue | alvo 80–100% |
| Estimation accuracy | Estimado vs. real | rastrear a tendência do viés |
| Burn-up chart | Progresso **e** linha de escopo em separado | revela scope creep que o burndown esconde |
| Escaped defects | Bugs encontrados **depois** do release | par de qualidade obrigatório de qualquer métrica de velocidade |
| Defect density | Defeitos por KLOC ou por ponto | usar com ressalva |

### 3.4 Prazo e custo — Valor Agregado (EVM)

| Sigla | Fórmula | Leitura |
|---|---|---|
| SPI | EV ÷ PV | > 1 adiantado; < 1 atrasado |
| CPI | EV ÷ AC | > 1 abaixo do orçado; < 1 estourando |
| SV / CV | EV − PV / EV − AC | variação absoluta de prazo / custo |
| EAC | BAC ÷ CPI (ou AC + (BAC − EV)) | custo final projetado |
| VAC | BAC − EAC | folga/estouro projetado |

**Versão leve p/ projeto curto:** acompanhar `% de burn do orçamento` vs. `% de entregável concluído`.
Alvo: ~80% de burn a ~80% de conclusão. Divergência = apertar escopo / change request, **em tempo
real, não no post-mortem**.

### 3.5 Financeiro de serviços / agência

| KPI | Fórmula | Alvo saudável |
|---|---|---|
| Billable utilization | horas faturáveis ÷ horas disponíveis | 65–85% conforme o papel |
| Realization rate | horas faturadas ÷ horas faturáveis trabalhadas | > 90% |
| Effective hourly rate | receita realizada ÷ horas trabalhadas | comparar com a tarifa nominal |
| Project gross margin | (receita − custo direto de entrega) ÷ receita | 50–60%; < 45% = problema de preço/alocação |
| Revenue per unit of scope | receita ÷ nº de módulos (ou histórias) | calibra a próxima proposta |
| Change orders faturados | Σ R$ de aditivos ÷ nº de mudanças de escopo | alerta se mudanças > 0 e aditivo = R$ 0 |

### 3.6 Pessoas e sustentabilidade — SPACE (Microsoft/GitHub/UVic, 2021)

| Dimensão | O que olha | Exemplos |
|---|---|---|
| **S**atisfaction & well-being | Satisfação do dev, risco de burnout | eNPS do dev, carga, crunch |
| **P**erformance | Qualidade e efeito do resultado | change failure rate, escaped defects |
| **A**ctivity | Volume de trabalho — **nunca isolado** | commits, PRs, deploys |
| **C**ommunication & collaboration | Qualidade do handoff e da coordenação | tempo de review, clareza de spec, atrito com o cliente |
| **E**fficiency & flow | Trabalho sem interrupção e sem fila | flow efficiency, tempo de foco, nº de bloqueios |

Regra: combinar ≥ 2–3 dimensões; nunca métrica única; nunca comparar indivíduos.

### 3.7 Cliente e resultado

| KPI | Como medir |
|---|---|
| On-time delivery rate | % de projetos entregues na data acordada ou antes (+ folga em dias) |
| CSAT / NPS do cliente | 1–2 perguntas logo após a entrega |
| Rework / revision rate | nº de rodadas de revisão por entregável antes do aceite |
| Defeitos no período de garantia | bugs reportados pelo cliente nas primeiras N semanas |
| Time-to-launch | início do prazo → site no ar no domínio do cliente |

---

## 4. Anti-padrões (o que NÃO medir)

- **Linhas de código como KPI** — incentiva volume, não valor; não reflete complexidade.
- **Velocity como meta ou comparação entre times** — a meta corrompe a estimativa.
- **Métrica de atividade isolada** (commits/dia) sem uma dimensão de qualidade/resultado ao lado.
- **Métrica individual para avaliar pessoas** — vira alvo e deixa de medir (lei de Goodhart).
- **Post-mortem financeiro** em vez de acompanhamento contínuo — o estouro chega tarde demais.

---

## 5. Cadência de coleta

Tudo em `docs/METRICAS-PROJETO.md`, exceto o fechamento (que gera `docs/ANALISE-PROJETO-<nome>.md`).

| Momento | O que registrar |
|---|---|
| **Kickoff** (Fase 0) | bloco 1.1: 4 datas · valor + moeda · canal/comissão/saque/tributo · valor/hora alvo |
| **Fase 3** | peso dos módulos, copiado do `ROADMAP.md` |
| **Ao fim de cada sessão** (Fase 4) | linha do timesheet (1.2) · abrir/fechar episódio de espera (1.3) |
| **Semanal** (projeto > 2 semanas) | burn de orçamento vs. % concluído · aging WIP · bloqueios abertos |
| **No fechamento** (Fase 5) | rodar `PROMPT-ANALISE-KPI.md` → `ANALISE-PROJETO-<nome>.md` · conjunto mínimo (seção 2) inteiro · 1 pergunta de CSAT ao cliente |
| **Fim da garantia** | defeitos reportados pelo cliente no período |

---

## 6. Fontes

DORA: [dora.dev/guides/dora-metrics](https://dora.dev/guides/dora-metrics/),
[história/redefinição 2024](https://dora.dev/insights/dora-metrics-history/),
[getdx](https://getdx.com/blog/dora-metrics/).
Fluxo: [businessmap — flow efficiency](https://businessmap.io/kanban-resources/kanban-analytics/flow-efficiency),
[Atlassian](https://www.atlassian.com/agile/project-management/kanban-metrics),
[Scrum.org](https://www.scrum.org/resources/blog/4-key-flow-metrics-and-how-use-them-scrums-events),
[NimbleWork](https://www.nimblework.com/kanban/kanban-metrics/).
Ágil: [Cortex — 24 métricas](https://www.cortex.io/post/24-agile-metrics),
[monday](https://monday.com/blog/rnd/agile-metrics/).
Valor agregado: [PMI](https://www.pmi.org/learning/library/practical-calculation-schedule-variance-7028),
[ProjectEngineer](https://www.projectengineer.net/the-earned-value-formulas/).
Financeiro de serviços: [AccountingDepartment](https://www.accountingdepartment.com/blog/master-your-metrics-utilization-realization-and-billable-rates),
[Kantata](https://www.kantata.com/blog/article/what-is-the-most-fundamental-kpi-for-services-businesses),
[CentSight](https://centsight.com/agency-finance/agency-kpis),
[Teamwork](https://www.teamwork.com/blog/project-profitability-metrics-agency-should-track/),
[Continuum](https://www.continuumpsa.io/single-post/the-post-mortem-problem-why-real-time-profitability-is-the-only-cure-for-overruns),
[Sengi](https://sengi.co/blog/freelance-profitability-metrics/).
SPACE: [getdx](https://getdx.com/blog/space-metrics/), [Jellyfish](https://jellyfish.co/library/space-framework/).
Geral: [Jellyfish — 15 KPIs](https://jellyfish.co/library/software-development-kpis/),
[Axify](https://axify.io/blog/kpi-software-development).
Acesso: set/2026.
