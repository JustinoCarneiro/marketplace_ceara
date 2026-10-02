---
tipo: bug
data: 2026-10-01
severidade: Alta
status: Resolvido — login e refresh recusam conta suspensa
resolvido_em: 2026-10-01
---

# Suspender um usuário (US26) não bloqueava nada: o flag `ativo` nunca era consultado

## Sintoma
A US26 diz que, ao suspender um usuário em abuso, "o acesso dele é bloqueado". O painel suspendia (o
status mudava para SUSPENSO e a ação ia para a auditoria), mas **o usuário suspenso continuava entrando e
renovando a sessão**: `AuthService.login` e `refresh` nunca olhavam `users.ativo`, e o `JwtAuthFilter`
também não. O único uso do campo era listar o status no painel e contar clientes ativos.

## Achado
Descoberto lendo o fluxo de autenticação para desenhar a recuperação de senha (US35): uma conta suspensa
poderia redefinir a senha e entrar. Não apareceu antes porque o teste de ponta a ponta do painel
(`05-usuarios.spec.ts`, "suspender e reativar persiste o status a cada passo") só confere que o **flag**
muda — nunca tenta usar a conta suspensa. Mesma classe de "teste verde que não checa o que promete".

## Correção
- `AuthService.login`: depois de conferir a senha, conta inativa → `422 ACCOUNT_SUSPENDED`. A ordem é
  de propósito: quem não sabe a senha não descobre que a conta está suspensa.
- `AuthService.refresh`: conta inativa → `INVALID_REFRESH_TOKEN` (mesma resposta de token inválido), sem
  rotacionar o token.
- A recuperação de senha não emite código para conta suspensa.

## Provas
- `AuthServiceTest`: suspensa com senha certa é bloqueada e nenhuma sessão nasce; com senha errada
  continua `INVALID_CREDENTIALS`; reativada volta a entrar; refresh de suspensa não renova. Os dois
  primeiros falhavam antes da correção (`Expecting code to raise a throwable`).
- E2E contra o Postgres (passo 24): suspende pelo endpoint real do admin e confere login, refresh e
  pedido de código; reativa e volta a entrar.

## Não coberto
O JWT de acesso já emitido continua válido até expirar (até 15 min): o `JwtAuthFilter` não consulta o
banco a cada requisição. Cortar na hora exigiria uma ida ao banco por requisição (ou uma lista de
revogação); fica como decisão se o risco de 15 min importar.

## Ligado a
- [[recuperacao-de-senha-codigo-por-email]]
