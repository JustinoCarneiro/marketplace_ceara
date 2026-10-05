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
