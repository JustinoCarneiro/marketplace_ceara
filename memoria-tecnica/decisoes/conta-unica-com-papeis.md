---
tipo: decisao
data: 2026-10-05
status: Ativa
---

# Conta única com papéis: a mesma pessoa é cliente e prestador numa conta só

## Contexto
Até aqui cliente e prestador eram **contas diferentes** (`users.role` guardava um papel só). Isso tinha dois custos:
- o prestador que precisa de um eletricista em casa não podia contratar: era preciso uma segunda conta, e a regra
  "uma pessoa = um CPF" (antifraude Camada 2) a barrava, já que o CPF dele era do prestador;
- a auto-contratação — a mesma pessoa nos dois lados do pedido, que fabrica reputação — só era barrada por id de conta,
  que não enxerga duas contas da mesma pessoa. O CPF único fechava isso, ao custo acima.

Uma tentativa intermediária (CPF único **por papel** + comparar o hash do CPF no aceite e no pagamento) funcionava, mas
deixava a mesma pessoa com duas contas, dois e-mails e a exclusão em dois lugares. Não chegou a ser commitada: o dono do
produto pediu o padrão do mercado (Uber, Airbnb) — **uma conta com dois papéis** — que é a Camada 3 de
`docs/PENDENCIAS_INTEGRIDADE.md`, antes prevista para a v2.

Mexe em autenticação, em PII (CPF) e no caminho do dinheiro: classe **R2**.

## Decisão
**Uma conta, vários papéis; o papel em uso vai no token.**

- **`user_papeis`** (V24): os papéis que a conta TEM. `users.role` passa a ser o papel **principal** (o do cadastro) — o
  contexto em que o login abre. Cliente: `{CLIENT}`; prestador: `{PROVIDER, CLIENT}` (**todo prestador também contrata**,
  inclusive os já existentes, pelo backfill da migration); admin: `{ADMIN}`, separado.
- **Contexto da sessão = claim `role` do JWT.** Os `@PreAuthorize` dos controllers continuam lendo só o token, então não
  mudou nenhuma regra de autorização: o prestador no modo cliente não vê endpoints de prestador e vice-versa.
- **`POST /api/v1/auth/switch-role`** `{ papel, refreshToken }`: emite um token e um refresh no novo papel e revoga o
  anterior. **`refreshToken` é obrigatório e precisa ser desta conta e ainda valer** (achado da revisão cruzada,
  2026-10-05 — ver abaixo). Só vale para papel que a conta **tem**; `ROLE_ADMIN` nunca. Erros: `ROLE_NOT_AVAILABLE`,
  `INVALID_ROLE`, `INVALID_REFRESH_TOKEN`. `refresh_tokens.papel` guarda o contexto: **renovar a sessão não devolve ao
  papel principal**.
- **`POST /api/v1/auth/become-provider`** `{ cpf, categoria, bio?, aceitouTermos, refreshToken }` (exige sessão de
  **cliente**): o cliente passa a prestar serviço **na mesma conta**. Valida o CPF, grava o hash, cria o perfil
  `EM_VERIFICACAO` (mesma verificação de qualquer prestador novo), dispara o background check e registra um novo aceite
  dos termos. Lê a conta com trava de linha: o toque duplo não cria dois perfis. `refreshToken` é obrigatório pelo mesmo
  motivo do `switch-role`. Erros: `ALREADY_PROVIDER`, `ROLE_NOT_AVAILABLE`, `INVALID_CPF`, `CPF_ALREADY_REGISTERED`,
  `CPF_MISMATCH`, `INVALID_REFRESH_TOKEN`.
- **`AuthResponse.papeis`**: todos os papéis da conta, para o app oferecer "alternar".
- **CPF volta a ser único na tabela inteira** (`users.cpf_hash UNIQUE`, V9): com uma conta por pessoa não há mais motivo
  para tolerar o mesmo CPF em duas contas. `verify-identity` e `become-provider` recusam CPF **diferente** do já confirmado
  (`CPF_MISMATCH`): antes o hash era sobrescrito, e trocar o CPF depois de confirmado burlaria banimento e unicidade.
- **Auto-contratação impossível por construção** — é sempre o mesmo `user_id`. Os pontos de aplicação:
  `ProposalService.accept` (já existia) e `ProposalService.create` (novo: proposta ao próprio pedido → `SELF_HIRE_FORBIDDEN`).
  E o que evita o tropeço antes do erro: o pedido da própria conta **não aparece na fila** do prestador
  (`listarDisponiveis`) e a conta **não aparece na própria busca** (`DiscoveryService.findNearby`).
- **App:** a sessão guarda `papeis`; o Perfil mostra "Alternar para modo cliente/prestador" (quem tem os dois) ou "Quero ser
  prestador" (quem só é cliente); `RootNavigator` troca a pilha pelo papel do token.

## O que fica igual de propósito
_Os três padrões abaixo (prestador reprovado ainda contrata; "clientes ativos" pelo papel principal; login abre no papel principal, sem sessão persistida) foram confirmados pelo dono do produto em 2026-10-05._

- **Status do prestador é do perfil, não da conta.** Reprovar ou suspender o **perfil de prestador** não bloqueia a conta:
  quem foi reprovado para prestar serviço continua podendo contratar. Suspender a **conta** (admin, US26) corta os dois papéis.
- **Exclusão de conta** é da conta: vale para os dois papéis e as recusas (pedido em curso, repasse a receber, reembolso a
  caminho) já eram por id, então cobrem os dois lados — provado no E2E (passo 48).
- **Painel admin:** `UserAdminDto.role` segue sendo o papel principal e ganhou `papeis`; a coluna mostra "Cliente +
  Prestador". A métrica "clientes ativos" conta contas cujo papel **principal** é cliente (um prestador não vira "cliente
  ativo" só por poder contratar).

## Alternativas descartadas
- **CPF único por papel + comparação do hash** (a tentativa acima): resolve o prestador contratar, mas mantém duas contas.
- **Uma conta com `roles` na claim, trocada no cliente sem o servidor:** o servidor perderia o controle de quem pode o quê.

## Consequências / limites aceitos
- **O access token do papel anterior segue válido até expirar (15 min)** depois de alternar — é do próprio dono, e o
  refresh anterior é revogado.
- **O app não persiste a sessão** (o store é em memória): fechar o app volta ao login, que abre no papel principal. Quem
  virou prestador pela conta de cliente abre como cliente e alterna.
- **Contas já duplicadas** (a mesma pessoa em duas contas) **não são fundidas**: o CPF era único na tabela inteira, então
  nunca existiram pares com o mesmo CPF; uma pessoa com duas contas por e-mails diferentes e CPFs diferentes não é detectável.
- **Titularidade do CPF não é validada** (só dígitos + unicidade): um CPF de terceiro ou inventado-válido passa. O freio
  real é o background check (hoje stub).
- **Fluxos Maestro (CI) não cobrem a troca de papel** — só o Playwright do app web (`mobile/tests/16-conta-unica.spec.ts`).
- **Pendência não resolvida (Codex, 2026-10-05, P2):** o backfill de `user_papeis` (V24) roda uma vez, na subida. Se uma
  instância ANTIGA (de antes da V24) cadastrar alguém DEPOIS do backfill — num deploy em rolagem com as duas versões
  no ar —, ela grava só `users.role`; a instância NOVA vê `papeis` vazio e recusa `switch-role`/`become-provider` para
  essa conta, e um prestador cadastrado assim fica sem o hash do CPF regravado. A demo (1 instância; Coolify troca o
  container, não roda as duas ao mesmo tempo) não expõe isso. Precisa de uma decisão de processo de deploy (impedir
  escrita da versão antiga durante o corte, ou reconciliar `user_papeis` a cada subida, não só na migração) — não
  implementado nesta revisão.
- **Aceite dos termos:** `become-provider` grava um novo aceite (a tabela é append-only). Se a assessoria quiser um documento
  de termos próprio para prestador, é uma versão nova do documento, não uma mudança de código.

## Revisão cruzada (Codex, 2026-10-05) — achados corrigidos nesta branch
- **P1 — um access token sozinho bastava para abrir uma sessão de 30 dias.** `switchRole` aceitava `refreshToken`
  ausente, revogado, expirado ou de OUTRA conta e seguia emitindo a sessão nova mesmo assim (o refresh era só
  "revogado se informado E válido E desta conta" — nunca uma condição para EMITIR); `become-provider` nem pedia
  refresh algum. Como o access token dura até 15 min e **não** é revogado pela troca de senha (só os refresh tokens
  são, via `revogarTodosDoUsuario`), um access token vazado nesse intervalo bastava para abrir uma sessão de 30 dias
  que sobrevivia à revogação de todas as outras. Corrigido: os dois agora **exigem e consomem** um refresh válido
  desta conta antes de emitir outro (`AuthService.consumirRefreshDaConta`, reusado pelos dois) — refresh inválido
  recusa com `INVALID_REFRESH_TOKEN`, nada é emitido. `refreshToken` passou de opcional a obrigatório nos dois DTOs.
- **P1 — `verifyIdentity` sem a mesma trava que `become-provider` já usava.** Com o CPF da conta ainda vazio, uma
  confirmação de identidade (cliente) e um cadastro de prestador (mesma conta) concorrentes liam o mesmo estado
  inicial; a última escrita vencia, deixando o hash da conta diferente do CPF cifrado no perfil — sem nunca disparar
  `CPF_MISMATCH` para avisar. Corrigido: `verifyIdentity` agora lê com `findByIdComTrava` (a mesma trava de
  `become-provider` e da exclusão de conta), serializando as duas. Prova determinística no E2E, passo 54 (molde dos
  passos 33/42).

## 2ª rodada de revisão cruzada (auto-revisão, 2026-10-09)
- **Refresh consumido de forma ATÔMICA, por um caminho só.** `consumirRefreshDaConta` (switch-role e become-provider) lia o token, conferia
  `isValid()` e o revogava com `save()`: duas chamadas simultâneas com o mesmo token passavam as duas em `isValid()` antes de qualquer uma
  revogar, e cada uma emitia uma sessão de 30 dias. Além disso, ele não conferia a conta ativa (o `refresh()` conferia) — as duas eram
  implementações separadas. Agora há um só `consumirRefresh` (existe, é do dono esperado quando informado, não expirou, não foi revogado,
  conta ativa) que consome com `UPDATE ... WHERE revogado = false` e recusa se 0 linhas foram afetadas; `refresh()`, `switchRole` e
  `tornarPrestador` passam por ele (o `refresh()` tinha o mesmo double-spend). Prova: E2E 67 (a linha do token presa por outra
  chamada em voo; a troca espera, recusa e nenhuma sessão extra nasce); a mutação (voltar ao `revoke()` + `save()`) reproduz o 200.
- **Limites aceitos, não corrigidos:** (1) se o servidor revoga o refresh e a resposta se perde no caminho (timeout, conexão caída), o
  app fica com o refresh antigo, já revogado, e o próximo `/auth/refresh` falha — o usuário é deslogado depois de uma troca que na
  verdade deu certo; é custo da troca revogar-antes-de-emitir, sem idempotência no cliente. (2) `switchRole` lê o usuário sem trava, o que
  hoje é inofensivo (não grava nada por essa referência); se uma mudança futura passar a gravar o `User` ali, vale a regra do
  [[exclusao-de-conta-por-anonimizacao]]: trava como primeira leitura. (3) `User.papeis` é `EAGER` (cada carga de `User` custa uma
  consulta a mais em `user_papeis`, inclusive login e listagem do admin); trocar por `LAZY` exige `JOIN FETCH` nas consultas que
  usam os papéis e quebraria o `emitirSessao` sobre um `User` já destacado.

## Efeito nos testes
Unitários: `UserPapeisTest`, `AuthServiceTest` (sessão, contexto, `switchRole`, CPF), `ProviderServiceTest` (cadastro com os
dois papéis, `tornarPrestador`), `ProposalServiceTest`, `ServiceRequestServiceTest`, `DiscoveryServiceTest`,
`JwtServiceTest`, controllers. E2E contra o Postgres real: passos 44–48 (contratar pela mesma conta, não contratar a si
mesma, "quero ser prestador", CPF no cadastro, exclusão com os dois papéis), passo 54 (a trava do CPF concorrente).
Painel: `admin/tests/15-papeis-da-conta.spec.ts`. App: `mobile/tests/16-conta-unica.spec.ts`.

## Ligado a
- US38 e US16 em `docs/spec.md`; `docs/PENDENCIAS_INTEGRIDADE.md` (Camadas 1–3).
- [[cpf-unico-para-o-prestador]] (o CPF único; a revisão por papel foi abandonada), [[chave-do-hash-do-cpf]] (a chave do HMAC,
  feita junto), [[exclusao-de-conta-por-anonimizacao]].
