---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# Exclusão de conta por anonimização no lugar (US36)

## Contexto
A exclusão de conta pelo app é exigência da App Store (Guideline 5.1.1(v)) e da Play Store, e direito do
titular (LGPD, art. 18, VI). Não existia: `DELETE` de usuário não havia, nem tela. Apagar a linha de `users`
**não é possível** sem quebrar o que o projeto precisa guardar: `service_requests.cliente_id`,
`proposals.prestador_id`, `terms_acceptance.user_id` (append-only, prova de consentimento),
`messages.remetente_id` e `admin_audit_log.admin_id` apontam para ela **sem** `ON DELETE CASCADE`; e as
transações financeiras e os pedidos concluídos têm de ficar (fiscal, disputas — LGPD art. 16, I e II). Só
`refresh_tokens`, `providers_profile` e `password_reset_codes` têm cascata.

Mexe em dado pessoal, em dinheiro retido e em autenticação: classe **R2**, com plano, revisão cruzada e
nada de merge antes dela.

## Decisão
**Anonimizar no lugar.** A linha de `users` fica; tudo que identifica a pessoa sai; o acesso se encerra.

`AccountDeletionService.excluir` (uma transação) faz, nesta ordem: lê o usuário **com trava de linha**;
recusa; limpa; anonimiza; publica o evento do e-mail. A lista inteira do que sai (e, por omissão, do que fica)
mora num lugar só, `AccountDeletionRepository` — `@Query`, então um erro de JPQL derruba a subida do app em
vez de estourar no dia de uma exclusão real.

**Recusas (nada é apagado):** senha incorreta (`INVALID_PASSWORD`); administrador (`ADMIN_CANNOT_DELETE`);
e `ACCOUNT_HAS_ACTIVE_ORDERS` quando há pedido `ACEITO`/`EM_ANDAMENTO`/`EM_DISPUTA` (cliente ou prestador), ou
**reembolso a caminho** (cliente: pedido `CANCELADO` com transação `RETIDO`), ou **repasse a receber**
(prestador: pedido `CONCLUIDO` com transação `RETIDO`). Assimetria de propósito: no Modelo A o repasse ao
prestador é manual (fila do admin) e o admin precisa da chave Pix dele — apagá-la deixaria o dinheiro sem
destino; o **cliente** que já confirmou a conclusão não tem nada a esperar que dependa dos dados dele.
Descobri esse estado lendo o código (`CONCLUIDO` + `RETIDO` é o caso comum do Modelo A), não pela spec.

**O que sai:**

| Onde | O quê |
|---|---|
| `users` | nome → "Usuário removido"; e-mail → `removido-<UUID aleatório>@excluido.invalid` (único, em domínio reservado — RFC 2606 — e **não derivado do id**: o id de um prestador é público, e um endereço previsível deixaria alguém cadastrá-lo antes e travar a exclusão, já que a coluna é UNIQUE); senha → hash de um segredo aleatório; `ativo=false`; `excluido_em` (V22); `cpf_hash` → NULL (exceção abaixo) |
| `providers_profile` | bio, chave Pix cifrada, CPF cifrado, localização → NULL; status → `SUSPENSO` (fora da busca, sem operar); categoria e nota ficam |
| `service_requests` do cliente | descrição, sugestão da IA, detalhes da disputa, localização → NULL; `PENDENTE`/`PROPOSTO` → `CANCELADO`; categoria, bairro, status e valores ficam |
| `proposals` | `ATIVA` → `ENCERRADA` (as do prestador e as dos pedidos do cliente); as `ACEITA` de serviços concluídos ficam |
| `service_media` | removidas (a casa, a voz); só a URL existia no banco |
| `messages` | `conteudo` → "[mensagem removida]" (a linha fica: quem falou, quando; a conversa da outra parte não ganha buracos) |
| `reviews` | só o `comentario` de quem avaliou; a **nota** fica (é reputação do avaliado) |
| `refresh_tokens`, `password_reset_codes` | apagados |

**O que fica, por premissa a confirmar com a assessoria** (`docs/PENDENCIAS_JURIDICAS.md`, item 5): `transactions`
e pedidos concluídos; `terms_acceptance` (inclui o **IP** do aceite — dado pessoal mantido como prova de
consentimento, ônus do controlador, LGPD art. 8º §2º); `sos_alerts` e o payload `SOS_TRIGGERED` do outbox
(**latitude/longitude** + `userId`); `denuncias` (inclui o texto livre do denunciante); `admin_audit_log`. Sem
prazo de expurgo por ora.

**Corte imediato do token.** `JwtAuthFilter` passou a conferir, a cada requisição, se a conta está ativa
(`existsByIdAndAtivoTrue`, uma consulta por chave primária). Antes o `ativo` só valia no login e no refresh: um
access token já emitido seguia valendo até 15 min depois da suspensão — e depois de uma exclusão isso seria
inaceitável. Vale também para suspensão (US26), o que é correção, não só consequência. Custo: uma consulta por
requisição autenticada.

**CPF e antifraude.** O hash do CPF só permanece se o prestador foi reprovado/suspenso pela moderação (ou a conta
estava suspensa): excluir não pode desfazer o vínculo que impede o mesmo CPF em outra conta. Uma conta limpa
pode voltar com o mesmo e-mail e CPF.

**Concorrência.** A leitura do usuário usa `PESSIMISTIC_WRITE`: o toque duplo no botão se enfileira; o segundo
pedido enxerga a conta já excluída e não repete a limpeza nem o e-mail. Provado de forma determinística no E2E
(passo 33), no molde do passo 26.

**Endpoint:** `POST /api/v1/users/me/delete` `{ senha }` → 204. `POST` de ação, como `/cancel` e `/dispute` — não
`DELETE` com corpo, que não tem semântica definida e alguns proxies descartam (a senha não pode se perder pelo
caminho). Erros de negócio em 422, inclusive `INVALID_PASSWORD`: 401 faria o app tratar o erro de senha como
sessão expirada.

**Aviso por e-mail** (`AccountDeleted` → `AccountDeletedMailListener`, `@Async` + `AFTER_COMMIT`): melhor esforço,
nunca desfaz a exclusão, o log só leva a classe da falha. O app **não promete** o e-mail (pode não haver SMTP).

## Consequências
- **Painel admin:** `UserAdminDto.status` ganhou `EXCLUIDO`; suspender, reativar e moderar conta excluída dão
  `ACCOUNT_DELETED` (422) — sem isso o `User.reativar()` lançaria `IllegalStateException` (500) e a tela mostraria
  uma conta apagada como "ATIVO" com botão "Suspender" (a tela tratava qualquer status ≠ `SUSPENSO` como ativo).
- **Conta de dois papéis (US38):** a exclusão é da conta e vale para os dois. As recusas já eram por id (pedido em curso como cliente
  **ou** como prestador, repasse a receber, reembolso a caminho), então cobrem os dois lados mesmo quando pedidas pelo outro modo; a
  limpeza anonimiza o perfil de prestador junto — provado no E2E (passo 48). [[conta-unica-com-papeis]]
- **Conta suspensa não exclui pelo app** (o token não vale mais): o pedido dela passa pelo suporte. É coerente com a
  retenção antifraude, mas é uma regra que o suporte precisa conhecer.
- Armazenamento de mídia é **stub** (`StorageServiceImpl.upload` devolve URL falsa): hoje não há arquivo a apagar.
  **Quando o S3/GCS real chegar, apagar o objeto precisa entrar** (via outbox, fora da transação), senão a linha some
  e a foto da casa fica no bucket.
- Fotos que um **prestador** anexou a pedidos de **outros** clientes (a avaliação reaproveita o endpoint de mídia)
  ficam: `service_media` não guarda o autor. Idem o texto de disputa que um prestador escreveu. Se isso importar,
  precisa de coluna de autor.
- **CPF do prestador — resolvido** em [[cpf-unico-para-o-prestador]]: o cadastro agora grava e consulta o hash (antes só o
  cliente, no 1º pagamento), então a retenção do hash do prestador reprovado, na exclusão, passa a ter efeito: ele não volta com o
  mesmo CPF.
- `users.cpf_cifrado` (coluna legada do V1) nenhuma entidade mapeia e nenhum código escreve: é sempre NULL.
- **Pedido preso em `PROPOSTO` — resolvido** em [[pedido-sem-prestador-volta-a-fila-cancela-e-expira]]: excluir a conta
  do prestador que tinha a ÚNICA proposta ativa devolve o pedido do cliente a `PENDENTE` (antes ficava preso e invisível).
- **Limites aceitos:** janela de milissegundos entre o filtro/checagem e o commit (um pedido criado por quem acabou
  de ser excluído passa pelo filtro); sem job de expurgo do histórico retido. Limite de tentativas de
  senha: resolvido junto para o login e para este endpoint — [[limite-de-tentativas-de-senha]].
- **Não fazer:** apagar a linha de `users`; reativar conta excluída; trocar a trava por leitura simples; fazer a
  exclusão depender do envio do e-mail.

## Revisão cruzada (Codex, 2026-10-05) — achados corrigidos nesta branch
- **P1 — escrita concorrente desfazia a anonimização.** `AuthService.verifyIdentity` lia o usuário sem trava
  (`findById`). Uma confirmação de identidade em voo ao mesmo tempo que uma exclusão podia ler a conta "viva"
  antes do commit da exclusão e, ao salvar depois, gravar de volta TODOS os campos do objeto em memória — nome,
  e-mail, `ativo`, `excluido_em` — desfazendo a anonimização (lost update: o `UPDATE` do Hibernate não é por
  coluna). Corrigido: `verifyIdentity` passou a usar a mesma trava (`findByIdComTrava`) de `excluir`, serializando
  as duas. Prova determinística (latch + duas transações, molde do passo 33): `E2EFluxoPrincipalTest`, passo 35.
- **P2 — `motivoDisputa` não saía.** A limpeza de texto livre só apagava `detalhesDisputa`; `motivoDisputa` (a API
  aceita como texto livre) ficava. Um pedido disputado e depois mediado continua elegível à exclusão (a recusa só
  olha `EM_DISPUTA` em curso), então o motivo escrito durante a disputa sobrevivia à exclusão. Corrigido: a mesma
  `UPDATE` agora limpa os dois.
- **P2 — texto de disputa escrito pelo PRESTADOR nunca saía.** `openDispute` aceita qualquer uma das duas partes,
  mas só a limpeza do lado do CLIENTE existia. Um prestador que escreveu o motivo/detalhes de uma disputa (já
  mediada) e depois exclui a própria conta não levava esse texto junto. Corrigido: nova consulta
  (`apagarMotivoDeDisputaDosPedidosOndeEhPrestador`) limpa motivo/detalhes nos pedidos onde o usuário excluído é o
  prestador com proposta aceita — sem tocar nos campos que pertencem ao cliente do pedido (descrição, localização).
- **P2 — `bairro`/`categoria` aceitavam qualquer texto.** A criação do pedido só limitava o tamanho do bairro e
  exigia categoria não vazia: um endereço, nome ou telefone passava, e esses campos sobrevivem à exclusão (são
  tratados como dado agregável nos relatórios do admin). `service_categories` (US28, painel admin) existe para
  outra finalidade e nasce vazia em todo ambiente — validar contra ela bloquearia todo pedido até o admin cadastrar
  algo à mão; não era um catálogo utilizável aqui. Corrigido com o que de fato é um catálogo fechado hoje: o
  `bairro` passou a aceitar só os 10 nomes que o app já mostra em chips (`shared/Bairro`, espelhando
  `NewRequestScreen.BAIRROS`); a `categoria` passou por uma validação de formato (só letra e espaço, sem dígito) —
  defesa, não catálogo fechado, documentada como tal. A exclusão também sanitiza bairro fora da lista gravado antes
  desta validação existir (`sanearBairroForaDaListaDosPedidosDoCliente`). **Falta fazer:** se um catálogo de
  categorias de verdade for necessário, `service_categories` precisa ser semeado e unificado com os valores que o
  app usa hoje (há uma inconsistência pré-existente de maiúsculas/acento entre telas do mobile, não mexida aqui).
- **P3 — o aviso (e-mail e tela) prometia mais do que é verdade.** "Guardamos apenas o histórico de pagamentos e
  de avaliações, sem nenhuma informação que identifique você" é falso: o IP do aceite dos termos, as coordenadas
  de um SOS e o texto de uma denúncia continuam. Corrigido: o texto agora promete só o que é verdade (nome/e-mail
  saem; alguns registros ficam, por exigência legal, sem o nome).

## 2ª rodada de revisão cruzada (auto-revisão, 2026-10-09) — o mesmo lost update, em outros escritores
A correção da 1ª rodada travou só `verifyIdentity`. A auto-revisão (dez ângulos independentes, cada achado conferido no código)
mostrou que **todo escritor que carrega `User`/`ProviderProfile` e grava a entidade inteira de volta** desfaz a anonimização se a
exclusão commitar no meio — o `UPDATE` do Hibernate não é por coluna e não há `@Version`/`@DynamicUpdate`:
- **`UserAdminService.suspender/reativar`**: lia o usuário sem trava. O `save` revertia nome, e-mail e `excluido_em` (PII de volta)
  e `reativar` podia reabrir a conta excluída. Agora lê com `findByIdComTrava`.
- **`ModerationService.moderar`**: lia o perfil sem trava (e a guarda `contaExcluida()` era uma leitura sem trava, que só pegava
  a exclusão já commitada). Agora trava o **usuário** primeiro — a mesma trava que a exclusão toma, e ela anonimiza o perfil junto.
  O método `ProviderProfile.contaExcluida()` ficou sem uso e saiu.
- **`ProviderService.atualizarChavePix`**: mesma coisa, e pior: o `save` trazia de volta bio, CPF cifrado e status, com a chave Pix
  nova junto. Trava o usuário primeiro; conta excluída responde `PROVIDER_NOT_FOUND`.
- **`ReviewService.atualizarNotaMedia`**: a avaliação é de OUTRO usuário (o cliente do pedido), então a corrida é a mais provável das
  quatro. Virou `ProviderProfileRepository.atualizarNotaMedia`, um `UPDATE` só da coluna (nota é histórico e fica), sem carregar o perfil.

**A trava só vale como PRIMEIRA leitura da linha.** Medido contra o Postgres real: `findByIdComTrava` sobre uma entidade que a
sessão já carregou devolve a MESMA instância com o estado antigo (o banco já tinha outro `nome`, a instância não). Reler "sob a
trava" não relê nada. Por isso cada correção toma a trava antes de qualquer outra leitura da entidade, e os testes unitários
conferem a ordem (`inOrder`). Prova no E2E, passos 36–39 (exclusão em voo segura a trava; o escritor concorrente espera e recusa).
A mutação (trava trocada por leitura simples) derruba os três primeiros.

**Limite aceito, não corrigido:** `PasswordResetService.redefinir` lê o usuário por e-mail sem trava e regrava a entidade. A corrida
com a exclusão exige o código de redefinição do próprio e-mail do dono e uma exclusão simultânea dele — o resultado seria o dono
desfazendo a própria exclusão. Corrigir pede trava por e-mail antes da primeira leitura e mexe em ~8 testes por uma janela de
milissegundos; fica registrado aqui.

## Efeito nos testes
Unitário (`AccountDeletionServiceTest`, `AccountControllerTest`, `AccountDeletedMailListenerTest`,
`UserAnonimizacaoTest`, `ProviderProfileAnonimizacaoTest`, `JwtAuthFilterTest`, admin) prova as regras; mock
**não** executa JPQL, então o **E2E contra o Postgres** (passos 27–34) confere cada tabela, as recusas, a trava e o
corte do token (e o passo 24 prova o corte imediato na suspensão). Também: `admin/tests/14-exclusao-de-conta.spec.ts`
e `mobile/tests/12-excluir-conta.spec.ts`.

## Ligado a
- US36 e US26 em `docs/spec.md`; `AccountDeletionService`, `AccountDeletionRepository`, `JwtAuthFilter`.
- [[recuperacao-de-senha-codigo-por-email]] (mesmo padrão de evento + listener `AFTER_COMMIT`; mesmo `UserMailSender`).
- [[prestador-verificado-para-operar]] (a conta excluída vira `SUSPENSO`, e o portão já recusa quem não é `VERIFICADO`).
- [[mercadopago-escrow-modelo-de-repasse]] (por que o repasse a receber impede a exclusão do prestador).
- [[ios-preparacao-sem-conta-apple]] (era a pendência nº 1 das lojas).
