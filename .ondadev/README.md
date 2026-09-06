# Failover de cota entre agentes — OndaDev 3.0

Quando a janela de uso de um agente acaba, o trabalho continua no outro **sem
reanálise completa do repositório**. Um agente por checkout por vez; o primeiro
para de escrever antes de o segundo começar.

## Monitorar a janela

| Agente | Comando | O que olhar |
| --- | --- | --- |
| Claude Code | `/usage` | % da janela de 5h e do limite semanal |
| Codex | `/status` | janela e limites da assinatura |

Não use a estimativa monetária do Claude Code para decidir consumo — a unidade
é **janela de uso aceita**, não preço por token.

## Limiares operacionais

| Uso da janela | Ação |
| --- | --- |
| **75%** | Rodar `bash scripts/ai-checkpoint.sh`. Commit de checkpoint opcional. Seguir trabalhando. |
| **90%** | Terminar **apenas a unidade atômica** em andamento (teste verde + commit limpo). Não começar módulo novo. |
| **100%** | Handoff completo: `ai-checkpoint.sh`, preencher as seções de raciocínio de `current.md`, **parar**. |

## Artefatos

```text
.ondadev/handoff/TEMPLATE.md   # versionado; estrutura do handoff
.ondadev/handoff/current.md    # ignorado pelo Git; gerado por ai-checkpoint.sh
scripts/ai-checkpoint.sh       # coleta só metadados seguros
```

`ai-checkpoint.sh` gera a **seção 0** de `current.md` (branch, último commit,
`git status`, `git diff --stat`, `git worktree list` e o resultado das
validações determinísticas), entre marcadores `ai-checkpoint:auto`. As seções 1
a 9 (objetivo, decisões, próximo passo, riscos…) são escritas pelo agente e o
script **não as toca** depois que `current.md` existe — pode rodar de novo à
vontade para refrescar os metadados sem perder o que escreveu. **Nunca** cole
conteúdo de arquivo, diff completo, valor de `.env` ou segredo no handoff.

A troca de agente no failover acontece **no mesmo checkout**: a sessão do
primeiro agente termina (cota) e o segundo assume o mesmo diretório de trabalho.
`current.md` é local e fica ali — não trafega pelo Git. Se a tarefa já estava
num worktree, o segundo agente continua **nesse mesmo worktree**. Worktree
separado é para trabalho paralelo (`setup/WORKTREE.md`), não para failover.

## Fluxo Claude → Codex

1. Claude: `bash scripts/ai-checkpoint.sh` e preenche as seções de raciocínio de
   `current.md` (objetivo, decisões, erros, próximo passo, riscos).
2. Claude: fecha a unidade atômica (`git add -A && git commit`, checkpoint
   opcional), marca "o primeiro agente parou?" e **encerra a sessão**.
3. Codex: no mesmo diretório, `bash scripts/ai-checkpoint.sh --no-tests` para
   reconferir branch/commit, e lê `current.md`.
4. Codex: continua a partir de "Próximo passo concreto" — sem reler o repo
   inteiro. Passa contrato + diff + logs de teste ao revisor, não pede
   reanálise completa.

## Fluxo Codex → Claude

1. Codex: `bash scripts/ai-checkpoint.sh` e preenche as seções de raciocínio.
2. Codex: fecha a unidade atômica, commita, marca "parou?" e **encerra a sessão**.
3. Claude: no mesmo diretório, lê `current.md` e reconfere com
   `bash scripts/ai-checkpoint.sh --no-tests`.
4. Claude: continua do "Próximo passo concreto".

## Regras invioláveis

- **Sem escrita concorrente.** O primeiro agente para antes de o segundo
  escrever. A checkbox "o primeiro agente parou?" em `current.md` é obrigatória.
- **`ANTHROPIC_API_KEY` nunca como fallback automático.** Se estiver definida, o
  Claude Code pode cobrar via API em vez de usar a assinatura. `ai-checkpoint.sh`
  avisa quando detecta a variável. Autenticação é sempre `claude auth login`.
- **Sem segredo no handoff.** `current.md` é ignorado pelo Git justamente para
  não virar canal de vazamento; ainda assim, não escreva segredo nele.
- **Checkpoint commit é opcional; squash antes do merge.** Commits de checkpoint
  (`chore: checkpoint <tarefa>`) podem ser espremidos num histórico limpo antes
  de qualquer merge. Nada de push/merge sem autorização humana.

## Simulação de continuidade (Gate G5)

Prova de que uma tarefa troca de agente e continua sem reanálise completa, **nas
duas direções**. Use uma tarefa mínima e reversível (R0).

1. **Claude → Codex.** Claude faz o passo 1 da tarefa, roda `ai-checkpoint.sh`,
   preenche `current.md` e para.
2. Codex abre **só** o `current.md` e responde: qual foi o último teste e o
   resultado, qual a pendência, qual o próximo passo. Executa o passo 2, roda
   `ai-checkpoint.sh`, para.
3. **Codex → Claude.** Claude abre só o `current.md`, confirma o passo 2, executa
   o passo 3 (limpar a tarefa), roda `ai-checkpoint.sh`.
4. G5 fecha quando os dois lados continuaram sem reler o repo e sem escrita
   concorrente, e nenhum segredo entrou no handoff.
