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
| `ROADMAP.md` + contratos | Fase 3 | Planta técnica: módulos, pesos e contratos Request/Response. |
| Commits / Small Releases | Fase 4 | Código testado e pronto para produção. |
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

*Onda · Documento de processo — base para modelagem BPMN. Documento vivo: versionar a cada
evolução do método. Toda decisão volta à pergunta-âncora:*
**é belo no design, fluido no uso e seguro por dentro?**
