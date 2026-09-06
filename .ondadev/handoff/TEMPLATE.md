<!--
  Template de handoff entre agentes (Claude <-> Codex) do OndaDev 3.0.
  Versionado. NÃO edite este arquivo para uma tarefa concreta: gere uma cópia
  em .ondadev/handoff/current.md (ignorado pelo Git) com:

      bash scripts/ai-checkpoint.sh

  A seção 0 é gerada e atualizada pelo script (entre os marcadores
  ai-checkpoint:auto). As seções 1 a 9 são suas e o script NÃO as toca depois
  de current.md existir — rode o script quantas vezes quiser para refrescar os
  metadados sem perder o que você escreveu.

  Nunca cole conteúdo de arquivo, diff completo, valor de variável ou segredo.
-->

# Handoff — <título curto da tarefa>

- **Direção:** Claude -> Codex  |  Codex -> Claude
- **Motivo:** cota a 100%  |  checkpoint a 75%  |  troca planejada  |  outro
- **Risco da tarefa:** R0 | R1 | R2

## 0. Metadados (gerado por ai-checkpoint.sh — não editar à mão)

<!-- ai-checkpoint:auto:start -->
_Rode `bash scripts/ai-checkpoint.sh` para preencher._
<!-- ai-checkpoint:auto:end -->

## 1. Objetivo

Uma frase: qual é o resultado observável esperado.

## 2. O primeiro agente parou de escrever?

- [ ] sim — obrigatório antes de o outro agente começar a escrever.

## 3. Arquivos em jogo

Comentário curto sobre o que cada mudança pendente da seção 0 representa:

- `caminho/arquivo` — o que está sendo feito ali

## 4. Último teste específico da tarefa

A tabela de validações determinísticas está na seção 0. Aqui, os testes
próprios da tarefa (se houver) — descreva o resultado, não cole log:

- `<comando>` — PASS | FAIL (<resumo de 1 linha>)

## 5. Decisões tomadas

- Decisão — motivo — o que ela impede/exige daqui pra frente.
- ADRs afetados: `docs/architecture/adr/NNNN-*.md`

## 6. Erros / bloqueios abertos

- Sintoma observável — hipótese de causa — onde parou de investigar.

## 7. Próximo passo concreto

1. A primeira ação que o próximo agente deve executar.
2. A segunda.

## 8. Riscos e pendências

- Risco — probabilidade/impacto — mitigação.
- Pendências que dependem de decisão de produto, credencial ou autorização externa.

## 9. Checklist de segurança do handoff

- [ ] Nenhum segredo, token, valor de `.env` ou dado de cliente neste arquivo.
- [ ] `ANTHROPIC_API_KEY` NÃO foi habilitada como fallback automático.
- [ ] Sem escrita concorrente: um agente por checkout por vez.
- [ ] `current.md` está fora do Git (é ignorado).
