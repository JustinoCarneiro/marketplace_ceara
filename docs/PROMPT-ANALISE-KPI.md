# Prompt — Análise de KPIs de projeto (reutilizável)

> Cole o bloco abaixo numa sessão de Claude Code (ou agente equivalente) **aberta na raiz do
> projeto a analisar**. Ele reproduz a análise-piloto do projeto Heliene Araújo usando o padrão de
> [`./METRICAS-KPI.md`](./METRICAS-KPI.md).
>
> Também dá para salvar como slash command em `~/.claude/commands/analise-kpi.md` e chamar com
> `/analise-kpi`.

---

```
Você é analista de projeto. Objetivo: produzir uma análise de KPIs DESTE projeto de
desenvolvimento, comparável entre projetos, a partir SÓ do que o repositório e os documentos
registram. Não invente dados.

## Passo 0 — Ler o registro de métricas
Leia `docs/METRICAS-PROJETO.md`. O que estiver preenchido lá (kickoff, timesheet, log de espera) é
a fonte — não pergunte de novo. Peça ao humano SÓ o que faltar, tipicamente:
- **custo/hora interno da empresa** (é o único campo que por padrão fica fora do repo — no sibling
  privado `<projeto>-docs-privados/` ou informado agora);
- a **entrega real**, se o projeto acabou e a data ainda não foi anotada no bloco de kickoff.
Se `docs/METRICAS-PROJETO.md` NÃO existir: peça todos os itens abaixo, trate a ausência de
timesheet/log como um achado da análise, e recomende criar o arquivo a partir do template do
`onda-starter` para os próximos.

Itens do kickoff (o que o arquivo deve conter / o que perguntar se faltar): nome do projeto ·
valor do contrato + moeda · 4 datas (aceite · início do prazo · limite contratual · entrega) ·
valor/hora alvo · canal (Workana/direto) com comissão %, taxa de saque % e regime tributário
(ou "n/a") · custo/hora interno.

## Passo 1 — Padrão
Se existir `docs/METRICAS-KPI.md` no repo, use as definições, fórmulas, alvos e o "conjunto mínimo"
de lá. Se não existir, use o RESUMO no fim deste prompt.

## Passo 2 — Coletar (marque explicitamente tudo que NÃO encontrar)
Git:
- `git log --reverse --date=short --format='%ad %s'` → 1º e último commit, timeline, marcos.
- Commits por dia; gaps de ≥ 2 dias entre commits → candidatos a espera/bloqueio. Se não houver
  registro do motivo em doc/board/memória, anote como "vão não explicado".
- Prefixos de commit (`feat`/`fix`/`chore`/`docs`/…) → proxy de flow distribution e de rework.
- `git log --numstat` → churn (adições/remoções); separe gerado (lock-file, tipos, migrations,
  importmap) do escrito à mão.
- `git shortlog -sne` → nº de pessoas.
Repositório / docs:
- Valor do contrato, prazo, escopo fechado: briefing / proposta / doc de escopo / roadmap.
- Nº de épicos, histórias, módulos e o peso/dias estimados (se houver blueprint/roadmap).
- Superfície: páginas, endpoints, integrações externas, modelo de dados (tabelas/campos),
  componentes, migrations.
- Testes: nº de arquivos e de casos (`it(`/`test(`), quais módulos têm teste.
- Deploy: histórico (Vercel/CI/tags/GitHub) → 1º deploy de produção, frequência, falhas/rollbacks.
- Timesheet e log de espera/impedimento: primeiro em `docs/METRICAS-PROJETO.md` (blocos 1.2 e
  1.3); depois board, notas, memória técnica, changelog de escopo. Se não existir em lugar nenhum,
  diga isso — é um achado, e a análise de fluxo/custo fica em faixa estimada.

## Passo 3 — Calcular por lente (use as fórmulas do padrão)
1. Prazo: duração contratual · lead time real · folga · dias ativos ÷ corridos · tempo até o
   "núcleo pronto" ÷ lead time · estimation accuracy (dias-peso vs. lead time real).
2. DORA (janela a partir do 1º deploy de produção): deployment frequency, lead time p/ mudança,
   change failure rate, failed deployment recovery time, deployment rework rate.
3. Fluxo: lead time vs. cycle time vs. FLOW EFFICIENCY (ativo ÷ (ativo+espera)) · aging WIP máximo ·
   flow distribution (por prefixo de commit) · wait/blocked time.
4. EVM: SPI (EV÷PV) vs. o contrato e vs. a estimativa · CPI (EV÷AC) · EAC · VAC · burn de esforço
   vs. % de entregável concluído.
5. Financeiro de serviços: effective hourly rate (bruto e líquido) · realization rate · gross
   margin com e sem custo de mão de obra · receita por módulo/história/dia · aditivos faturados
   vs. mudanças de escopo.
6. SPACE: sinal em cada dimensão (S: crunch/horários; P: incidentes+testes; A: volume; C: atrito
   de handoff; E: flow efficiency/aging). Nunca uma métrica única; nunca comparar indivíduos.
7. Cliente: on-time delivery + folga · CSAT/NPS (ou "não coletado") · rework/revisões · defeitos
   na garantia · time-to-launch.

## Regras
- Todo valor derivado de horas não registradas = faixa "(est.)". NUNCA fabrique um timesheet.
  Sem horas: diga "incalculável" para CPI e margem real, e apresente o cenário parametrizado
  (N h × R$X/h) que o humano informou.
- Cada achado vem com a IMPLICAÇÃO de planejamento, não só o número.
- Use a moeda, as unidades e o vocabulário do próprio projeto.
- Marque cada KPI com status 🟢 / 🟡 / 🔴 contra o alvo.
- Proibido: linhas de código como KPI de valor; velocity como meta ou comparação; métrica
  individual para avaliar pessoa; concluir sem apontar as lacunas de dados.

## Saída
1. Escreva `docs/ANALISE-PROJETO-<NOME>.md` com estas seções, na ordem:
   Ficha do projeto (fatos) · Prazo e cronograma · DORA · Fluxo · Valor Agregado (EVM) ·
   Financeiro de serviços · Qualidade · SPACE · Cliente e resultado · Leitura consolidada e
   implicações de planejamento · Snapshot para o histórico da empresa (bloco de texto plano).
2. Preencha o "conjunto mínimo (~12 KPIs)" do padrão numa tabela.
3. Liste as lacunas de dados e o que instrumentar já no próximo projeto. Se `docs/METRICAS-PROJETO.md`
   não existia, crie-o agora a partir do template do `onda-starter` com o que foi possível
   reconstruir (marcando o que é retroativo/estimado).
4. Se o humano pedir, gere também um painel HTML de página única (tema claro/escuro, sem libs
   externas) com os KPIs de topo + as tabelas por lente.
Não altere código do projeto. Não commite. Se este repo tiver a "Regra de Ouro" de sincronizar
spec com Trello/Jira, note que um doc de análise NÃO é spec e não dispara os scripts.

## RESUMO DO PADRÃO (fallback — use só se não houver docs/METRICAS-KPI.md)
Conjunto mínimo: 4 datas; lead/cycle time + flow efficiency; on-time + folga; horas por fase;
effective hourly rate; gross margin com mão de obra; receita por módulo/história; mudanças de
escopo (absorvidas vs. faturadas); estimation accuracy; escaped defects + incidentes (nº/sev/
recovery); rework rate; CSAT.
DORA: deployment frequency; lead time p/ mudança (commit→prod, elite <1d); change failure rate
(elite 0–15%); failed deployment recovery time (elite <1h); deployment rework rate.
Fluxo: lead time (com espera) vs. cycle time (só ativo); flow efficiency = ativo ÷ (ativo+espera),
15–40% comum e >40% bom; throughput; WIP; Lei de Little (lead ≈ WIP÷throughput); aging WIP; flow
distribution (feature/bug/débito/risco); wait time (fila, esperado) vs. blocked time (inesperado).
Ágil: velocity (só forecast do próprio time); commitment accuracy/say-do (alvo 80–100%);
estimation accuracy; burn-up (separa progresso de escopo); escaped defects; defect density.
EVM: SPI=EV÷PV (>1 adiantado); CPI=EV÷AC (>1 abaixo do orçado); EAC=BAC÷CPI; VAC=BAC−EAC;
versão leve = % burn de orçamento vs. % entregável concluído, alvo ~80/80, em tempo real.
Financeiro de serviços: billable utilization = faturáveis ÷ disponíveis (65–85%); realization =
faturadas ÷ trabalhadas (>90%); effective hourly rate = receita realizada ÷ horas; project gross
margin = (receita − custo direto) ÷ receita (50–60% saudável, <45% problema); receita por unidade
de escopo; change orders faturados (alerta se mudança>0 e aditivo=R$0).
SPACE (Microsoft/GitHub 2021): Satisfaction & well-being; Performance; Activity (nunca isolada);
Communication & collaboration; Efficiency & flow. Combinar ≥2–3; nunca comparar pessoas.
Cliente: on-time delivery rate; CSAT/NPS pós-entrega; rework/revision rate; defeitos na garantia;
time-to-launch.
Anti-padrões: LoC como KPI; velocity como meta/comparação; atividade isolada sem qualidade ao
lado; métrica individual para avaliação de pessoa; post-mortem financeiro em vez de contínuo.
```
