---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# Limite de tentativas de senha: por conta, no banco, com trava de linha (US37)

## Contexto
Nada limitava as tentativas de senha: o login e, desde a US36, a confirmação da exclusão de conta aceitavam palpites
sem fim. A recuperação de senha por e-mail já tinha o próprio limite (5 erros fecham o código); a senha em si não.
Na exclusão de conta o risco é concreto: quem tem um token roubado (15 min) e tenta a senha apaga a conta alheia.

## Decisão
**5 erros de senha seguidos bloqueiam a conta por 15 minutos** (`429 TOO_MANY_ATTEMPTS` + `Retry-After`, mesmo com a
senha certa), no login e na exclusão de conta, com o **mesmo contador** da conta. Acerto zera; passado o bloqueio o
próximo erro recomeça do zero. Configurável: `marketplace.password-attempts.max-failures` / `lock-seconds`.

- **Por conta, no banco** (`users.senha_falhas`, `senha_bloqueada_ate`, V23), como o limite da recuperação de senha.
  Em memória e por IP não serviriam: o backend pode ter mais de uma instância e o IP atrás do proxy é do proxy.
- **Trava de linha** (`findByEmailComTrava` no login, `findByIdComTrava` na exclusão): palpites simultâneos se
  enfileiram em vez de lerem o mesmo contador. Provado no E2E (passo 37): 30 palpites paralelos, exatamente 5 avaliados.
- **O contador é gravado mesmo com a exceção.** A transação voltaria inteira com a exceção e o limite nunca valeria,
  então o login e a exclusão declaram `noRollbackFor`, **só** para `PasswordMismatchException` e
  `TooManyAttemptsException` (e não para `BusinessException`): no fluxo da exclusão, uma outra recusa lançada depois de
  uma escrita parcial também seria confirmada.
- **Bloqueado, a senha nem é conferida:** o BCrypt não vira oráculo de palpite e a senha certa não entra.
- **429**, não 422: é limite de taxa, e o `Retry-After` diz quando voltar. A mensagem arredonda os minutos para cima.
- **Saída:** a redefinição de senha por e-mail (US35) zera o contador e encerra o bloqueio.

## Consequências
- **Trade-off assumido (DoS):** quem sabe o e-mail de alguém pode bloqueá-lo por 15 minutos. Aceito porque o bloqueio é
  curto, a recuperação por e-mail o encerra e o ataque exige insistência contínua. Se virar problema: bloqueio por
  (conta, IP) ou progressivo, com o IP real vindo do proxy.
- **Taxa residual:** 5 palpites por 15 min por conta (≈ 480 por dia). Com BCrypt e senha de 8+ caracteres é pouco; se
  precisar de mais, o bloqueio pode escalar a cada reincidência.
- O e-mail desconhecido responde como senha errada e **não conta** nada. Quem quer saber se um e-mail existe já tem o
  cadastro (`EMAIL_IN_USE`); este limite não pretende esconder isso.
- Não cobre o `refresh` (token, não senha) nem o admin à parte: o admin usa o mesmo login e herda o limite.
- O painel não mostra nem desbloqueia: o desbloqueio é esperar ou recuperar a senha por e-mail.

## Revisão cruzada (Codex, 2026-10-05) — achados corrigidos nesta branch
- **P2 — o acerto de senha não sobrevivia a uma recusa de negócio logo depois.** `login()`/`excluir()` zeravam o
  contador e, na MESMA transação, podiam lançar `ACCOUNT_SUSPENDED` (US26) ou `ACCOUNT_HAS_ACTIVE_ORDERS` (US36) —
  exceções que corretamente desfazem tudo o mais, mas também desfaziam o acerto que tinha acabado de zerar o
  contador. Quem informava a senha certa, mas era barrado por outro motivo, voltava a contar de um número errado
  no próximo erro. Corrigido extraindo o acerto/erro para `PasswordAuthenticator`, numa transação PRÓPRIA
  (`REQUIRES_NEW`) que sempre commita; `login()`/`excluir()` continuam livres para desfazer o resto. Prova
  determinística no E2E, passos 40–41 (login e exclusão).
- **Regressão própria, achada e corrigida no processo:** a primeira versão da correção manteve `login()` com
  `@Transactional` "por garantia", mesmo sem nenhuma leitura/escrita própria (tudo passa pela transação do
  `PasswordAuthenticator` e pelo `save` do Spring Data). Isso quebrou o passo 37 (30 logins simultâneos): o Spring
  abre a conexão da transação envolvente assim que o método é chamado, mesmo vazia — 30 chamadas ao mesmo tempo
  seguravam 30 conexões ociosas enquanto esperavam a transação de dentro, o pool esgotava e sobravam respostas que
  não eram nem 422 nem 429. `login()` ficou sem `@Transactional`.
- **P3 — `Retry-After` podia anunciar menos tempo do que o bloqueio realmente dura.** `exigirLiberada` capava o
  tempo restante pela configuração ATUAL (`Math.min` com `lock-seconds`); reduzir a configuração com um bloqueio já
  gravado sob o valor antigo fazia a resposta mentir a duração. Corrigido: o tempo vem só do que está gravado em
  `senha_bloqueada_ate`.
- **P2 — demo sem SMTP configurado:** aceito como limitação por ora (sem credenciais de e-mail disponíveis); ver
  `memoria-tecnica/decisoes/vps-deploy-marketplace-ceara.md` (se existir) ou a configuração de ambiente da demo —
  sem canal de recuperação, quem é bloqueado nesse ambiente só tem a saída de esperar os 15 minutos.

## Efeito nos testes
`UserTentativasDeSenhaTest`, `PasswordAttemptsTest`, `PasswordAuthenticatorTest` (novo), `AuthServiceTest`,
`AccountDeletionServiceTest`, `PasswordResetServiceTest`, `ErrorControllerAdviceTest` e o E2E (passos 35–38 e 40–41; o
tempo passa por `UPDATE` em `senha_bloqueada_ate`). A mutação mostrou que o que só o Postgres real prova (persistência
do contador, trava de linha, a transação própria do acerto) é pego pelo E2E e não pelo unitário.

## Ligado a
- US37, US35 e US36 em `docs/spec.md`; `PasswordAttempts`, `AuthService.login`, `AccountDeletionService`.
- [[exclusao-de-conta-por-anonimizacao]] (a pendência de origem) e [[recuperacao-de-senha-codigo-por-email]].
