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
- **`POST /api/v1/auth/switch-role`** `{ papel, refreshToken? }`: emite um token e um refresh no novo papel (e revoga o
  refresh anterior, se informado). Só vale para papel que a conta **tem**; `ROLE_ADMIN` nunca. Erros: `ROLE_NOT_AVAILABLE`,
  `INVALID_ROLE`. `refresh_tokens.papel` guarda o contexto: **renovar a sessão não devolve ao papel principal**.
- **`POST /api/v1/auth/become-provider`** `{ cpf, categoria, bio?, aceitouTermos }` (exige sessão de **cliente**): o
  cliente passa a prestar serviço **na mesma conta**. Valida o CPF, grava o hash, cria o perfil `EM_VERIFICACAO` (mesma
  verificação de qualquer prestador novo), dispara o background check e registra um novo aceite dos termos. Lê a conta com
  trava de linha: o toque duplo não cria dois perfis. Erros: `ALREADY_PROVIDER`, `ROLE_NOT_AVAILABLE`, `INVALID_CPF`,
  `CPF_ALREADY_REGISTERED`, `CPF_MISMATCH`.
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
- **Aceite dos termos:** `become-provider` grava um novo aceite (a tabela é append-only). Se a assessoria quiser um documento
  de termos próprio para prestador, é uma versão nova do documento, não uma mudança de código.

## Efeito nos testes
Unitários: `UserPapeisTest`, `AuthServiceTest` (sessão, contexto, `switchRole`, CPF), `ProviderServiceTest` (cadastro com os
dois papéis, `tornarPrestador`), `ProposalServiceTest`, `ServiceRequestServiceTest`, `DiscoveryServiceTest`,
`JwtServiceTest`, controllers. E2E contra o Postgres real: passos 44–48 (contratar pela mesma conta, não contratar a si
mesma, "quero ser prestador", CPF no cadastro, exclusão com os dois papéis). Painel: `admin/tests/15-papeis-da-conta.spec.ts`.
App: `mobile/tests/16-conta-unica.spec.ts`.

## Ligado a
- US38 e US16 em `docs/spec.md`; `docs/PENDENCIAS_INTEGRIDADE.md` (Camadas 1–3).
- [[cpf-unico-para-o-prestador]] (o CPF único; a revisão por papel foi abandonada), [[chave-do-hash-do-cpf]] (a chave do HMAC,
  feita junto), [[exclusao-de-conta-por-anonimizacao]].
