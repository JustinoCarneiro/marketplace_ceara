---
tipo: bug
data: 2026-08-21
severidade: Média
status: Resolvido (workaround manual permanente)
---

# Jira team-managed: campo→layout é gap de API real; delete de issue é permissão, não API

## Sintoma
Automatizando `jira_sync.py` e chamadas diretas à API REST v3 contra os projetos Jira da Onda
(todos team-managed, template Kanban, em `ondaenterprise.atlassian.net`), algumas operações falham
com o mesmo token que cria/atualiza/transiciona issues sem nenhum problema:

1. Associar um campo customizado (ex: `Peso`, `Dias Estimados`) ao layout de uma issue — não existe
   endpoint que faça isso em projeto team-managed. **Confirmado gap de API de verdade**: a UI faz
   isso normalmente (Project settings → Work item types → campos), a API não tem equivalente.
2. `GET /rest/api/3/permissionscheme` e `/rest/api/3/priority` retornam 401 (não teve tempo de
   confirmar se a UI consegue algo equivalente — anotado como gap de API por ora, não confirmado
   se é permissão ou ausência de endpoint).
3. `DELETE /rest/api/3/issue/{key}` retorna 403 `"You do not have permission to delete issues in
   this project."` — **NÃO é gap de API** (ver Causa raiz corrigida abaixo).

## Causa raiz
**Item 1 (campo→layout) é limitação real de superfície de API**: projetos team-managed (ex-"next-
gen") no Jira Cloud rodam num subsistema de configuração mais simples que projetos company-managed
— administração de layout de campo só é exposta pela UI, não pela API REST v3 pública.

**Item 3 (delete de issue) NÃO é isso — correção de 21/08/2026**: inicialmente documentado aqui
como o mesmo tipo de limitação de API do item 1. Errado. Ao investigar por que o **próprio usuário,
pela UI**, também não conseguia deletar (mensagem exibida: *"We can't delete these work items — You
need permission from an admin first"*), a causa real apareceu: **o "space" (nome do projeto
team-managed na UI) tinha 0 papéis atribuídos** — `Project settings → People` mostrava "Where's
everybody? There is no one in this space", nem o criador/dono tinha o papel Administrator
explicitamente. Ou seja, a API e a UI concordam — ninguém tinha permissão de deletar, porque
ninguém tinha papel nenhum atribuído no espaço. Isso é uma lacuna de **configuração inicial do
projeto** (nenhum papel foi atribuído na criação via API), não uma limitação categórica da
plataforma. Uma vez que alguém é adicionado como Administrator em `People`, o delete deve
funcionar — não testado ainda se passa a funcionar via API também depois disso (só testado
reconhecer a causa; a correção do papel em si não foi feita via API nesta sessão, ver nota abaixo).

## Solução
1. **Campo→layout**: só manual. **Project settings → Work item types → (tipo) → aba de campos**
   (nesse Jira aparece como lista + dropdown "Select Field...", não drag-and-drop) → adicionar o
   campo.
2. **Delete de issue bloqueado por "0 roles"**: `Project settings → People → Add people` → adicionar
   a própria conta (ou quem for administrar) com papel **Administrator**. Repetir por projeto — não
   é uma config de site, é por espaço/projeto individualmente.
3. **Não tentei corrigir o papel via API** (`POST /rest/api/3/project/{key}/role/{roleId}` — o
   `GET` equivalente funciona e devolve os papéis disponíveis, incluindo o id de "Administrators"):
   o harness do Claude Code (classificador de modo automático) bloqueou a chamada por ser uma ação
   de gestão de permissão/acesso, categoria tratada como sensível — não foi um erro do Jira. Se
   precisar automatizar isso no futuro, essa chamada especificamente pode precisar de permissão
   explícita configurada no `settings.json` do Claude Code, não é um bloqueio da Atlassian.

**Regra geral:** só assumir "não dá pra fazer isso nesse tipo de projeto" depois de checar se não é
simplesmente **falta de papel/permissão atribuído** primeiro (`Project settings → People`) — mais
barato de verificar do que caçar limitação de API, e no caso do delete foi exatamente isso.
