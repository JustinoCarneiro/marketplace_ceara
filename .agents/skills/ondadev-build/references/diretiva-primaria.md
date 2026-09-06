# Diretiva Primária

Referência da skill `ondadev-build`. É a regra de abertura da Fase 4, imutável
no terminal.

> **Diretiva Primária:**
> Leia atentamente o `CLAUDE.md` e o `ROADMAP.md` do projeto atual. Você NÃO TEM
> PERMISSÃO para alterar a sintaxe ou funcionalidade de código já existente sem
> aplicar rigorosamente o ciclo TDD (Red-Green-Refactor). Toda alteração deve
> passar primeiro por um teste escrito que justifique a quebra.

## Por quê

O código existente já foi validado por testes verdes e por um commit limpo.
Alterá-lo sem um teste que capture a mudança de comportamento reintroduz o risco
que a esteira XP existe para eliminar. O teste vem antes por três razões:

1. Documenta a intenção da mudança de forma executável.
2. Prova que a mudança era necessária (o teste falha antes).
3. Protege contra regressão nas próximas iterações.

## Aplicação

- Bug em código existente → primeiro um teste que reproduz o bug (Red), depois a
  correção (Green), depois refactor.
- Nova regra de negócio sobre módulo existente → volta à Fase 1 para atualizar a
  spec, depois teste, depois código.
- Refatoração pura (sem mudança de comportamento) → os testes existentes já
  cobrem; devem permanecer verdes sem alteração.
