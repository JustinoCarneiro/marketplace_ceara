# Onda · Metodologia de Desenvolvimento

### Playbook de Engenharia — Onda-Dev

> **Belo no design. Fluido no uso. Sólido na segurança.**
> **v2.0 · Junho/2026**

---

> **Propósito.** Formalizar o ciclo de vida de desenvolvimento da Onda como um processo
> padronizado, repetível e previsível. Entra um pedido de cliente — que pode ser uma única
> frase; sai software em produção, com **prazo calculado** (não estimado no chute) e
> **qualidade verificada** em cada etapa (não apenas prometida), independentemente do tipo de
> projeto: e-commerce, app, landing page, sistema interno ou automação.

Este playbook foi desenhado para o cenário de um **desenvolvedor solo operando com IA de alta
performance** — Antigravity como IDE/orquestrador e Claude Code no terminal. Ele se apoia nos
princípios do *Agile Vibe Code* e do *Extreme Programming (XP)* com IA: no lugar da burocracia
voltada à gestão de pessoas, uma **iteratividade de engenharia** que garante adaptação a
mudanças, entregas contínuas e altíssima qualidade técnica.

---

## 1. A caixa-preta (visão executiva)

O cliente não precisa entender o interior do processo; precisa confiar que, dada qualquer
entrada válida, a saída é consistente. A promessa é simples:

| Entrada | Processo | Saída |
|---|---|---|
| Pedido do cliente (pode ser uma única frase) | 6 fases sequenciais com fluxo Kanban e TDD | Software rodando em produção |

---

## 2. Atores (as raias do processo)

Três papéis percorrem todas as fases. A IA sempre opera sob supervisão humana — ela gera, o
humano valida.

| Ator | Papel no processo |
|---|---|
| **Cliente** | Origem do pedido. Fornece requisitos, aprova o visual e valida a entrega. Participante externo. |
| **Dev / Humano (Onda)** | Conduz o processo, faz as perguntas certas, decide arquitetura, valida as saídas da IA e fala com o cliente. |
| **IA / Agentes (Claude Code)** | Gera artefatos (specs, layout, código, testes), executa o TDD e roda revisões — sempre sob supervisão do humano. |

---

## 3. Artefatos (os entregáveis palpáveis)

A previsibilidade nasce de entregáveis concretos a cada etapa, que comprovam o avanço para o
cliente e para o negócio.

| Artefato | Nasce na | Função |
|---|---|---|
| `CLAUDE.md` + `spec.md` | Fase 1 | Fonte única da verdade: épicos, histórias de usuário, stack e arquitetura. |
| `tokens.css` + `DESIGN.md` | Fase 2 | Identidade visual **do projeto** — do cliente, nunca a da Onda. |
| Protótipo estático | Fase 2 | Interface aprovável, com dados fictícios. |
| `ROADMAP.md` + contratos | Fase 3 | Planta técnica: módulos, pesos e contratos Request/Response. Também é o quadro Kanban do projeto — ver seção 12. |
| Commits / Small Releases | Fase 4 | Código testado e pronto para produção. |
| `memoria-tecnica/` | Fase 4 (nasce vazia na Fase 0) | Memória técnica viva: bugs cabeludos e decisões tomadas fora da spec — ver seção 11. |
| Deploy | Fase 5 | Software em produção. |

---

## 4. O macrofluxo — as 6 fases

A execução obedece a uma linha de montagem. Cada fase tem entrada, atividades, um ponto de
decisão e uma saída clara.

```
Fase 0 → Fase 1 → Fase 2 → Fase 3 → Fase 4 → Fase 5
Scaffolding · Spec Viva · Layout & Congelamento · Blueprint · Esteira XP · Homologação
```

### Fase 0 — Scaffolding
*Prepara o terreno antes de qualquer conversa de escopo.*

- **Entrada:** pedido aceito, projeto iniciado.
- **Atividades:** clonar o template `onda-starter`; ativar a skill de perfil conforme o tipo (e-commerce, app, LP, sistema, automação).
- **Saída:** repositório preparado, contexto enxuto.
- **Ator:** Humano + IA.

### Fase 1 — Spec Viva (gera o `CLAUDE.md`)
*Transforma o pedido (muitas vezes vago) na especificação viva do projeto.*

- **Entrada:** pedido do cliente.
- **Atividades:**
  1. Briefing que destrava o escopo (público, dores, regras, volume).
  2. Mapeamento de épicos.
  3. Quebra em histórias de usuário (“Como [X], quero [Y] para [Z]”).
  4. Critérios de aceite no formato dado-quando-então.
  5. Geração do `CLAUDE.md` + `spec.md`.
- **Decisão — G1:** *Requisitos claros o suficiente?* Não → volta ao briefing. Sim → avança.
- **Saída:** `CLAUDE.md` (fonte única da verdade) + `spec.md`.
- **Ator:** Humano conduz · Cliente fornece · IA redige.

### Fase 2 — Layout & Congelamento Visual
*Mitiga o risco de o cliente mudar o fluxo depois e destruir o banco. É condicional.*

- **Entrada:** `CLAUDE.md`.
- **Decisão — G2:** *O cliente tem identidade visual?*
  - **Não → Fase 2a (Direção Visual):** briefing de marca curto → 2–3 direções divergentes em *style tiles* → cliente escolhe → refino → `tokens.css` + `DESIGN.md`. Parte-se de um starter neutro e acessível, **nunca da marca da Onda**.
  - **Sim → vai direto para a Fase 2b.**
- **Fase 2b (Layout):** a IA lê o `CLAUDE.md` + a identidade do projeto e gera o front 100% estático com dados fictícios (hierarquia, responsividade, acessibilidade AA, estados de erro/carregamento).
- **Decisão — G3:** *Layout aprovado?* Não → revisa. Sim → **Congelamento Visual**: a partir daqui, mudar o visual é mudança de escopo.
- **Saída:** protótipo aprovado e congelado; `tokens.css` definido.
- **Ator:** IA gera · Cliente aprova · Humano media.

> **Nota de prazo.** Se houve criação de identidade (Fase 2a), o termo da Fase 2 cresce de ~2 para ~4 dias — e isso é precificado como item próprio.

### Fase 3 — Blueprint (gera o `ROADMAP.md`)
*A planta técnica, desenhada antes de codificar.*

- **Entrada:** visual congelado.
- **Atividades:**
  1. Modelagem do banco (tabelas e relacionamentos exatos).
  2. Divisão do sistema em **módulos independentes**.
  3. Pesagem de cada módulo (Complexidade + Risco).
  4. Definição dos **contratos de API** (Request/Response) — antes de qualquer código.
  5. Rastreabilidade história ↔ módulo (relação N:1).
  6. Cada módulo nasce com `**Status:** ⬜ Pendente` — é o quadro Kanban do projeto (ver seção 12).
- **Saída:** `ROADMAP.md` + contratos + **prazo técnico calculado**.
- **Ator:** Humano decide arquitetura · IA gera o roteiro.

### Fase 4 — Esteira XP (codificação por módulo)
*Fluxo contínuo (Kanban), consumindo o `ROADMAP.md`. Pesado no terminal (Claude Code).*

- **Abertura — Diretiva Primária:** “Leia o `CLAUDE.md` e o `ROADMAP.md`; a partir de agora, não altere a sintaxe do código existente.”
- **Ciclo TDD:**
  - **Red:** IA escreve testes com mocks; eles falham.
  - **Green:** só o código necessário para passar.
  - **Refactor:** aplica DRY e otimiza sem quebrar os testes.
  - **Segurança:** agente `revisor-seguranca` nos módulos de risco.
  - **Commit limpo** (Small Release).
  - **Atualiza o status do módulo no `ROADMAP.md`** de `⬜ Pendente` para `✅ Concluído (data)` assim que os testes fecham verdes e o commit é feito — é isso que faz do `ROADMAP.md` o quadro Kanban vivo do projeto (seção 12), não um board externo.
- **Memória técnica:** antes de investigar um bug ou decidir algo fora da spec, consultar `memoria-tecnica/`; ao resolver algo não-trivial, registrar lá (ver seção 11 — critério de quando vale a pena).
- **Decisões:**
  - **G4 — Testes verdes?** Não → volta ao TDD.
  - **G5 — Pedido de mudança?** Sim → **retorno à Fase 1**.
  - **G6 — Mais módulos na fila?** Sim → puxa o próximo · Não → avança.
- **Saída:** todos os módulos testados e commitados.
- **Ator:** IA codifica · Humano supervisiona e valida.

> **O coração entra cedo.** O módulo de maior risco (gateway de pagamento, máquina de estados,
> motor de permissões) é puxado no **início** da Fase 4 — nunca no fim. Falhar cedo é barato;
> falhar tarde compromete a entrega.

### Fase 5 — Homologação, Deploy e Encerramento
*A entrega oficial.*

- **Entrada:** módulos completos.
- **Atividades:**
  1. Smoke test local: subir o ambiente via Docker e rodar toda a esteira de testes.
  2. Validação humana de ponta a ponta.
  3. Revisão final de segurança.
  4. Revisão manual da `memoria-tecnica/`: checar se ficou desatualizada e podar notas triviais (a IA popula por conta própria em melhor esforço, não é garantido — não assumir que está completa sem olhar).
- **Decisão — G7:** *Smoke test + validação OK?* Não → **retorno à Fase 4**. Sim → avança.
- **Saída:** **Deploy via CI/CD** → software em produção.
- **Ator:** Humano valida · IA executa · Cliente recebe.

---

## 5. Gateways e retornos (o mapa de decisões)

Os sete gateways exclusivos (XOR) e os loops que o processo precisa representar — o núcleo da
governança do fluxo.

| # | Onde | Pergunta | Sim | Não |
|---|---|---|---|---|
| G1 | Fim da Fase 1 | Requisitos claros? | Vai para Fase 2 | Volta ao briefing |
| G2 | Início da Fase 2 | Cliente tem identidade? | Vai para Fase 2b | Entra na Fase 2a |
| G3 | Fim da Fase 2 | Layout aprovado? | Congelamento → Fase 3 | Revisa o layout |
| G4 | Dentro da Fase 4 | Testes verdes? | Commit | Volta ao ciclo TDD |
| G5 | Dentro da Fase 4 | Pedido de mudança? | Retorna à Fase 1 | Continua |
| G6 | Dentro da Fase 4 | Mais módulos na fila? | Puxa o próximo | Vai para Fase 5 |
| G7 | Dentro da Fase 5 | Smoke test + validação OK? | Deploy | Retorna à Fase 4 |

---

## 6. Gestão de mudanças no fluxo

Mudança não é exceção — é parte do processo, com pontos de retorno bem definidos para que nada
destrua trabalho já feito.

**Retorno à Fase 1 — funcionalidade nova.** Se o cliente pede uma feature nova no meio da Fase 4
(ex.: “adicionar PIX”), **não se codifica na hora**. O fluxo volta à Fase 1: atualiza-se o
`CLAUDE.md` com a nova história, a IA lê a regra, atualiza os testes e **só então** codifica.

**Retorno à Fase 2 — mudança no visual congelado.** Alterar o visual já aprovado enquanto o
backend está em andamento impacta as tabelas do sistema. Caracteriza **mudança de escopo** e
exige aditivo de prazo, medido com o mesmo peso de módulo.

---

## 7. Previsibilidade — o prazo calculado

Para um desenvolvedor solo com IA, estimar por “horas” é ineficaz. A métrica é o **peso dos
módulos** do `ROADMAP.md` (Complexidade + Risco), não horas.

| Peso | Dias | Características |
|---|---|---|
| Pequeno | 1–2d | Código mecânico, baixo risco. CRUDs simples, telas estáticas, perfis. |
| Médio | 3–4d | Lógica intermediária, mais atenção no TDD. Relatórios, APIs externas. |
| Grande | 5–7d | Coração do sistema, alto risco. Gateway de pagamento, máquina de estados, segurança e permissões. |

```
Prazo = Fase 2 (≈2d com identidade / ≈4d sem) + Σ(dias dos módulos das Fases 3 e 4) + 2d (Fase 5)
```

O mesmo método precifica **aditivos**: uma funcionalidade nova pedida no meio é medida com o
mesmo peso e soma ao prazo de forma consistente.

### Exemplo prático

Cliente traz a própria identidade (Fase 2 = 2d). O `ROADMAP.md` tem 2 módulos pequenos (≈4d) e
1 módulo grande (≈7d) — 11 dias de engenharia. Somando a homologação:

| Parcela | Dias |
|---|---|
| Fase 2 — identidade já trazida pelo cliente | 2 |
| 2 módulos pequenos | 4 |
| 1 módulo grande | 7 |
| Fase 5 — homologação e deploy | 2 |
| **Prazo técnico blindado** | **15 dias úteis** |

---

## 8. Definição de pronto — o filtro de qualidade

Nenhuma entrega fecha sem responder “sim” às três camadas da marca. Faltou uma, não está pronto.

- **Belo — no design.** Bate com a identidade do projeto aprovada na Fase 2: hierarquia clara, estética cuidada.
- **Fluido — nas funcionalidades.** Funciona sem atrito; estados de erro e carregamento tratados. Se trava ou falha, não é fluido.
- **Sólido — na segurança.** TDD verde, dados protegidos, revisão de segurança aprovada. Qualidade verificada, não prometida.

---

## 9. Divisão de ferramentas por fase

Todo o ciclo roda dentro do **VSCode + extensão Claude Code** — interface visual, markdown renderizado e acesso direto ao repositório. A única exceção é a Fase 2b, que usa o Claude Design para geração de interface.

| Fase | Ferramenta | Por quê |
|---|---|---|
| 1 · Spec Viva | Claude Code / VSCode | Escreve `CLAUDE.md` e `spec.md` direto no repositório. |
| 2a · Direção Visual | Claude Code / VSCode | Decisão estratégica de marca; gera `tokens.css` + `DESIGN.md`. |
| 2b · Layout | Claude Design | Recurso exclusivo de geração visual de interface. |
| 3 · Blueprint | Claude Code / VSCode | Escreve `ROADMAP.md` direto no repositório. |
| 4 · Esteira XP / TDD | Claude Code / VSCode | Lê/escreve código e roda testes. |
| 5 · Homologação | Claude Code / VSCode | Docker, scripts e deploy. |

### Ponto de entrada por fase

| Fase | Onde | Comando / ação |
|---|---|---|
| 0 · Scaffolding | Terminal | `git clone onda-starter nome-do-projeto && code .` |
| 1 · Spec Viva | VSCode / Claude Code | `/onda-spec-viva` |
| 2b · Layout | VSCode / Claude Code | `/onda-layout` |
| 3 · Blueprint | VSCode / Claude Code | `/onda-blueprint` |
| 4 · Esteira XP | VSCode / Claude Code | Diretiva Primária + ciclo TDD por módulo |
| 5 · Homologação | VSCode / Claude Code | Smoke test + validação + deploy |

---

## 10. Configuração e migração de ambiente

O ecossistema de desenvolvimento da Onda — skills, agentes e ferramentas — está versionado no próprio `onda-starter`, dentro da pasta `setup/`. Isso garante que qualquer máquina nova seja configurada de forma idêntica em minutos, sem dependência de memória ou configuração manual.

### Estrutura de setup

```
onda-starter/
└── setup/
    ├── install.sh              ← script de instalação automatizada
    ├── CHECKLIST.md            ← passos manuais restantes
    └── claude/
        ├── commands/           ← todas as skills (/onda-novo, /onda-spec-viva, perfis…)
        └── agents/             ← agentes (revisor-seguranca, testador-tdd, explorador)
```

### Situação: troca ou formatação de máquina

Ao migrar para uma nova máquina, o processo completo é:

```bash
# 1. Clonar o template (que contém o setup)
git clone https://github.com/JustinoCarneiro/onda-starter.git
cd onda-starter

# 2. Rodar o instalador — configura todas as ferramentas e skills automaticamente
bash setup/install.sh

# 3. Completar os passos manuais (auth, VSCode, PAT)
cat setup/CHECKLIST.md
```

O `install.sh` instala e configura automaticamente: Git, Node.js (via nvm), Docker, GitHub CLI e Claude Code, e copia todas as skills e agentes para `~/.claude/`.

### O que é automatizado vs. manual

| Item | Automatizado | Manual |
|---|---|---|
| Git + identidade | ✅ | — |
| Node.js 20 via nvm | ✅ | — |
| Docker Engine | ✅ | — |
| GitHub CLI (`gh`) | ✅ | — |
| Claude Code CLI | ✅ | — |
| Skills e agentes `~/.claude/` | ✅ | — |
| Login Anthropic (`claude auth login`) | — | ✅ |
| Login GitHub (`gh auth login`) | — | ✅ |
| GitHub PAT (repo + workflow) | — | ✅ |
| VSCode + extensão Claude Code | — | ✅ |
| Chaves SSH para GitHub | — | ✅ (opcional) |

### Situação: atualizar skills numa máquina existente

Quando as skills ou agentes forem evoluídos, reaplicar na máquina local com:

```bash
cp setup/claude/commands/*.md ~/.claude/commands/
cp setup/claude/agents/*.md ~/.claude/agents/
```

> **Regra:** `setup/claude/` é a fonte única da verdade para o ecossistema de skills. Toda nova skill ou agente deve ser adicionada lá antes de ser copiada para `~/.claude/`.

---

## 11. Memória Técnica Viva — padrão Obsidian

*Piloto validado no projeto Sistema Melvin (jul/2026) antes de virar padrão.*

### O que é e por quê

`CLAUDE.md` e `ROADMAP.md` cobrem o que foi planejado. Mas todo projeto acumula, ao vivo, conhecimento
que não estava na spec: um bug que exigiu investigação de causa raiz, uma decisão técnica tomada no
meio da Fase 4 por um motivo que não é óbvio olhando só o código. Hoje esse conhecimento cai na memória
do próprio agente — que é isolada por projeto, não versionada, e não sobrevive a uma troca de máquina
ou ferramenta.

`memoria-tecnica/` resolve isso como uma pasta comum dentro do repositório (não uma ferramenta externa):
markdown puro, sem lock-in, que o Obsidian sabe abrir como *vault* pra navegação em grafo — mas que o
Claude Code lê e escreve normalmente com ou sem o Obsidian aberto.

### Estrutura

```
memoria-tecnica/
├── _index.md         ← painel de entrada, lista bugs e decisões
├── bugs/              ← causa raiz de bugs não-triviais já resolvidos
├── decisoes/          ← decisões técnicas tomadas fora da spec original
└── templates/         ← modelo de nota (bug.md, decisao.md)
```

Nasce vazia na Fase 0 (scaffolding) — não é um problema ela não ter nada útil ainda nos primeiros
módulos; o valor se acumula com o tempo de vida do projeto, igual acontece com o Changelog de Escopo
do `CLAUDE.md`.

### Critério de quando criar uma nota

Documentar só quando pelo menos um destes for verdade — evitar isso vira ruído e destrói o valor do
padrão:
- Exigiu investigação real (a causa não era óbvia a partir do stack trace ou do código).
- A causa está fora do código-fonte visível (config de infra, comportamento de dependência externa, nginx, etc.).
- É uma decisão que contradiz ou refina algo que já foi decidido antes — e alguém (humano ou IA) vai
  precisar saber disso antes de mexer ali de novo.

Não criar nota se o fato já tem um lar melhor e visível (ex.: já é critério de aceite no `CLAUDE.md`,
ou já tem um aviso dedicado num checklist) — isso duplicaria a fonte de verdade em vez de complementá-la.

### Ressalvas

- **Não é automático.** A IA populando a `memoria-tecnica/` sozinha é melhor esforço, seguindo a
  instrução do `CLAUDE.md` — não uma garantia de sistema. Por isso a Fase 5 tem um passo de revisão
  manual (ver seção anterior).
- **Escreva para humano ler primeiro.** O ganho de ser indexável por IA é consequência do formato
  (markdown + links), não o objetivo — uma nota que só um agente entende não serve pro humano que
  vai reler meses depois.
- **Um vault por projeto, nunca um vault único pra todos os projetos da Onda.** Cada projeto é de um
  cliente diferente — misturar bugs/decisões de clientes distintos num grafo só vaza contexto entre
  eles. Padrões técnicos genuinamente reaproveitáveis entre projetos (se/quando surgirem) vivem em
  outro lugar, nunca dentro da `memoria-tecnica/` de um cliente específico.

---

## 12. Rastreio de Progresso — Status por Módulo no ROADMAP.md

*Formalizado em 30/07/2026 — o padrão estrutural (status por módulo dentro do `ROADMAP.md`) já
existia organicamente em mais de um projeto da Onda antes de virar regra escrita — só que cada um
com um vocabulário próprio (ver "Padronização de vocabulário" abaixo).*

### O que é e por quê

A seção 4 sempre falou em "fluxo Kanban" na Fase 4, mas nunca disse **onde** esse Kanban mora.
Este é o padrão oficial: **o quadro Kanban não é uma ferramenta externa (Trello, Jira) — é o
próprio `ROADMAP.md`.**

### Convenção — vocabulário único, sem variações

Cada módulo, ao nascer na Fase 3 (Blueprint), recebe:
```
**Status:** ⬜ Pendente
```
Ao ser concluído na Fase 4 (testes verdes, commitado), atualiza pra:
```
**Status:** ✅ Concluído (2026-07-09) — backend (137/137 testes...)
```
Um resumo curto do que foi coberto (contagem de testes, achado relevante) é bem-vindo, mas
opcional. Um estado intermediário `🔄 Em andamento` pode ser usado se o módulo estiver em
progresso há mais de uma sessão.

**São exatamente estes 3 marcadores, sempre com esse texto exato — `⬜ Pendente`, `🔄 Em
andamento`, `✅ Concluído` — em todos os projetos da Onda.** Não usar variações como `COMPLETO`,
`DONE`, `Feito`, `Finalizado` etc., mesmo que pareçam sinônimos óbvios — o valor do padrão é
poder olhar o `ROADMAP.md` de qualquer projeto da Onda e reconhecer o status sem reaprender o
vocabulário daquele projeto específico. (Achado nesta mesma formalização: Sistema Melvin e SAW
Hub já tinham convergido pra essa estrutura de forma independente, mas com palavras diferentes
entre si — `COMPLETO` vs `concluído` — corrigido pra um só termo em todos.)

### Distinção do Changelog de Escopo

Isso **não substitui** o Changelog de Escopo (tabela usada no `CLAUDE.md` de outros projetos,
como o Sistema Melvin) — são artefatos com propósitos diferentes:
- **Status por módulo (`ROADMAP.md`)** — progresso: o que já foi construído, módulo a módulo.
- **Changelog de Escopo (`CLAUDE.md`)** — histórico: mudanças de escopo e decisões que alteraram
  o que estava planejado, com data e impacto.

Um projeto pode (e frequentemente deve) ter os dois — não são concorrentes.

---

## 13. Padrão de Gestão Visual (Kanban 9 Colunas)

O Trello da Onda não é uma ferramenta separada da documentação técnica; ele é o seu espelho visual. **Regra de Ouro da Sincronização:** Toda e qualquer especificação funcional criada, alterada ou deletada (nos arquivos `CLAUDE.md`, `ROADMAP.md` ou `spec.md`) deve obrigatoriamente engatilhar o utilitário local `./scripts/trello_sync.py` para sincronizar o quadro do projeto correspondente. A documentação e o Trello são a mesma entidade.

Para comportar o fluxo das 5 Fases da Onda (do Design ao Deploy), o quadro oficial de Kanban no Trello deve ter *exatamente* e *apenas* as seguintes **9 listas (colunas)** na ordem especificada:

1. **📚 Base de Conhecimento (Docs / Memória Técnica):**
   - Usado para abrigar links para documentações oficiais e notas de memória técnica rápidas (ex: resoluções de problemas recorrentes e causas raiz, vivendo diretamente no board).
2. **❄️ Icebox (Banco de Ideias):**
   - Ideias, sugestões e pedidos de melhoria que ainda não foram priorizados ou detalhados. Separa claramente o que "talvez aconteça" do backlog real de trabalho.
3. **📋 Backlog (Especificações e Épicos):**
   - Tarefas e épicos aprovados e detalhados.
   - **Regra de Ouro (Regra 2a):** *Documentação antes da codificação*. Os cards aqui devem ser "spec-driven", contendo a especificação da feature ou o link para o `spec.md`, antes de irem para desenvolvimento.
4. **🏗️ Requisitos Não-Funcionais & Arquitetura:**
   - Coluna dedicada exclusivamente para débitos técnicos, tarefas de segurança, performance, LGPD, decisões de infraestrutura e arquitetura/design. Preenche a lacuna de não misturar melhorias estruturais com entregas funcionais (épicos/histórias) do negócio.
5. **🎯 A Fazer (To Do / Ready):**
   - Cards do backlog que estão priorizados, refinados, e prontos para serem puxados pela equipe no ciclo atual.
6. **⚙️ Em Execução (Doing / In Progress):**
   - O que está sendo ativamente codificado ou configurado neste exato momento.
7. **🔍 Code Review / Testes:**
   - Revisão de código, testes unitários, testes E2E e validação técnica interna antes de ir para o ambiente do cliente.
8. **🧪 UAT (Homologação / Validação do Cliente):**
   - Validação da entrega (Aceitação do Usuário) junto ao cliente ou key user. Alinha-se diretamente com a **Fase 5** da metodologia Onda.
9. **✅ Concluído (Done 🎉):**
   - Tudo que já foi testado, aprovado pelo cliente, homologado e entregue em produção.

### 13.1 Padrão de Escrita dos Cartões (Spec-Driven & Checklists)

Para que o board funcione como uma ferramenta ágil real (inspirada no Scrum) e não apenas um amontoado de lembretes, a escrita interna dos cartões deve seguir regras rígidas:

- **Clareza de Épicos e Histórias de Usuário:** O título e a descrição devem comunicar claramente o valor de negócio (ex: "Como usuário, quero X para poder Y"). O contexto do requisito ou o link para o `spec.md` deve estar explícito.
- **Checklists Contextuais ("Critérios de Aceite"):** É terminantemente proibido o uso de listas genéricas (boilerplates). Todo cartão refinado (movido para "A Fazer") deve conter uma lista nativa nomeada exclusivamente como `"Critérios de Aceite"`.
- **Granularidade Técnica:** Os itens desse checklist devem traduzir a regra de negócio em entregas técnicas tangíveis (ex: *Criar índice PostGIS, Construir endpoint GET /search, Validar regra de no-show*). O cartão só atinge 100% de conclusão quando todos esses critérios técnicos específicos são validados.

### 13.2 Padrão de Etiquetas (Tags)

Para garantir rastreabilidade de responsabilidades e filtragem visual rápida, os quadros utilizam **apenas 6 etiquetas oficiais**, abolindo a criação de tags ad-hoc (ex: "Database", "Integração"). Todo cartão de requisito deve ter pelo menos uma destas alçadas associadas:

- 🔵 **Frontend (UI/UX):** Telas, layouts, SPA, mobile, responsividade.
- 🟢 **Backend (Regras & APIs):** Serviços, banco de dados, regras de negócio, endpoints.
- 🟡 **Arquitetura / Segurança:** Decisões de modelagem estrutural, autenticação, permissões e LGPD.
- 🟠 **Infraestrutura / Cloud:** DevOps, pipelines CI/CD, buckets (S3), Docker, deploys.
- 🟣 **Design / Documentação:** Pesquisa visual, criação de tokens, prototipagem (Figma) e documentação técnica.
- 🔴 **Bug / Débito Técnico:** Correções de defeitos ou refatorações emergenciais de performance.

---

*Onda · Documento de processo — base para modelagem BPMN. Documento vivo: versionar a cada
evolução do método. Toda decisão volta à pergunta-âncora:*
**é belo no design, fluido no uso e seguro por dentro?**
