---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# Pedido sem prestador: volta à fila, o cliente cancela e, sem andamento por 15 dias, expira

## Contexto
Um pedido que recebia proposta ia a `PROPOSTO`, e dali só saía pelo aceite. Se a única proposta ia embora — o cliente a
recusava (`reject` só marcava a proposta `RECUSADA`) ou o prestador excluía a conta (US36) — o pedido ficava em `PROPOSTO`
**sem proposta ativa**: a fila dos prestadores só lista `PENDENTE`, então ninguém mais o via, e o cliente **não tinha como
cancelá-lo** (`cancel` só valia em `ACEITO` e `EM_ANDAMENTO`). Um pedido `PENDENTE` esquecido também nunca saía da fila. A
máquina de estados do `CLAUDE.md` previa "recusa / expira → `CANCELADO`", que nunca foi implementado. Achado ao
implementar a exclusão de conta; a decisão de produto veio do usuário (volta à fila + cancelar + 15 dias).

## Decisão
1. **Sem proposta ativa, volta à fila.** Recusada a última proposta ativa de um pedido `PROPOSTO`, ele volta a `PENDENTE`
   (`ProposalService.reject`). Recusar **uma** de várias não mexe no pedido. Na exclusão de conta do prestador, o pedido
   em que a proposta dele era a única volta a `PENDENTE` (`AccountDeletionRepository`, **antes** de encerrar as propostas
   dele, porque é por elas que acha os pedidos).
   Recusar é diferente de cancelar: o cliente continua precisando do serviço, e uma recusa só não deve matar o pedido.
2. **O cliente cancela `PENDENTE`/`PROPOSTO`** (`POST /service-requests/{id}/cancel`): vai a `CANCELADO`, as propostas ativas
   se encerram e **não há reembolso** — nesses estados não existe transação (o dinheiro só entra no aceite). Só o cliente dono:
   o prestador com proposta ativa ainda não é parte do pedido (`isParticipante` recusa). `ACEITO`/`EM_ANDAMENTO` seguem como
   estavam, com reembolso.
3. **Expiração em 15 dias** (`marketplace.request.expiration-days`): `PedidoExpiracaoJob`, de hora em hora, cancela
   `PENDENTE`/`PROPOSTO` **sem andamento** e encerra as propostas ativas. Andamento = mudança de estado **ou proposta nova**
   (um pedido que ainda recebe ofertas não expira). Quando o pedido volta à fila (item 1) o relógio recomeça, porque a
   mudança de estado é andamento. `ACEITO` em diante nunca expira sozinho: tem prestador e dinheiro.

## Consequências
- **A máquina de estados mudou** (`CLAUDE.md`, spec US15/US16): `PROPOSTO → PENDENTE` quando a última proposta ativa sai;
  `PENDENTE | PROPOSTO → CANCELADO` por cancelamento do cliente ou expiração.
- A expiração é em lote (duas `UPDATE` por até 500 pedidos) e confere o estado de novo na escrita: um aceite que chegou entre
  a consulta e o `UPDATE` não é desfeito.
- **Limite aceito:** o cliente não é avisado quando o pedido expira (não há canal de notificação ao cliente hoje); ele vê
  `CANCELADO` na lista. Quando houver push, avisar uma semana antes é o passo natural.
- **Fora do escopo:** prestador **barrado pelo admin** (suspenso/reprovado) continua com a proposta `ATIVA`, e o cliente só
  descobre ao tentar aceitar (`PROVIDER_NOT_VERIFIED`). Isso é anterior e não muda aqui.
- Mobile: o botão "Cancelar pedido" aparece também em `PENDENTE`/`PROPOSTO`, só para o cliente. A confirmação passou a usar
  `window.confirm` no web (o `Alert.alert` não faz nada lá, e a demo é o build web), o que também conserta o cancelamento
  de `ACEITO` na demo.

## Revisão cruzada (Codex, 2026-10-05) — achados corrigidos nesta branch
- **P1 — `cancel()` sobrescrevia um aceite concorrente sem deixar rastro.** Lia o status, decidia cancelar e só depois
  gravava — sem reconferir na escrita. Entre a leitura e a gravação, o pedido podia ter sido aceito e o pagamento
  iniciado numa transação concorrente; o `save()` do objeto em memória sobrescrevia esse aceite com `CANCELADO` sem
  erro nem reembolso (a transação ainda nem existia quando `cancel()` checou). Corrigido com um `UPDATE` guardado
  (`cancelarSeEmEstadoCancelavel`) que reconfere o estado ATUAL na própria escrita: 0 linhas afetadas relê e informa o
  estado de verdade, em vez de seguir como se tivesse cancelado. Prova determinística em
  `cancel_corridaComAceiteConcorrente_naoSobrescreve_eLancaComOEstadoDeVerdade`.
- **Lacuna relacionada, achada ao corrigir a de cima:** com a cobrança enfileirada no outbox antes do cancelamento e a
  confirmação do gateway chegando depois, `cancel()` não encontrava transação `RETIDO` (ela ainda nem existia) e não
  criava reembolso — o dinheiro ficaria retido para sempre num pedido já `CANCELADO`. Corrigido em
  `PaymentService.confirmPayment` (o evento confirmado que dirige o estado financeiro, princípio do `CLAUDE.md`):
  confirmado o pagamento de um pedido já cancelado, retém e devolve na mesma hora.
- **P2 — a expiração em lote só reconferia o status na escrita, não o prazo nem a proposta nova.** Uma proposta nova
  podia chegar entre a consulta (que montou a lista) e o `UPDATE`; ela reinicia o prazo (é andamento), mas checar só o
  status não via isso, e o pedido expirava apesar da proposta ser recente. Corrigido reconferindo as TRÊS condições
  (status, prazo e nenhuma proposta recente) na própria escrita (`cancelarSemAndamento`, agora com 3 parâmetros); e
  `encerrarAtivasDosPedidos` passou a rodar só sobre o que a escrita **de fato** cancelou (`idsComStatus`, lido depois,
  na mesma transação), nunca sobre o lote inteiro da consulta — senão uma proposta nova chegada a tempo ainda podia ser
  encerrada por engano, mesmo com o pedido correto não expirando.
- **P2 — reabertura concorrente podia esconder uma proposta nova.** `reabrirSeNaoHaPropostaAtiva` (chamado por
  `reject` ao recusar a última proposta ativa) e `create()` (proposta nova) liam e escreviam o pedido sem se bloquear.
  Uma proposta nova gravada bem entre a conferência e a escrita da reabertura ficava invisível: o pedido voltava a
  `PENDENTE` com uma proposta `ATIVA` escondida — nem a fila dos prestadores, nem o cliente percebiam, o pedido
  simplesmente não aparecia mais pra ninguém, sem erro nenhum. Diferente dos achados acima (um guard na escrita
  bastava: a condição inteira cabe numa única consulta), aqui há DOIS caminhos de escrita independentes disputando o
  mesmo pedido — um guard em cada um não os serializa entre si. Corrigido com trava de escrita na linha
  (`ServiceRequestRepository.findByIdComTrava`, `@Lock(PESSIMISTIC_WRITE)`, o mesmo padrão já usado em `UserRepository`
  para a exclusão de conta e o limite de tentativas): `create()` e a reabertura de `reject()` agora travam o mesmo
  pedido antes de decidir, o que força quem chega depois a reler o estado JÁ commitado pelo primeiro. Prova
  determinística no E2E, passo 45 (molde dos passos 33/42): a reabertura fica parada ANTES do commit, já com a trava;
  só então a proposta nova é disparada pela API real — ela espera, e ao continuar vê o `PENDENTE` já commitado, abre a
  proposta normalmente e o pedido volta a `PROPOSTO` sem perder nada.
  **Achado relacionado — eu havia dito que não precisava de mudança, e estava ERRADO (corrigido na rodada 3, E2E 69 da branch de correção):**
  a reabertura da exclusão de conta do prestador (`AccountDeletionRepository.reabrirPedidosSoComPropostaDoPrestador`) é um `UPDATE` em lote com
  `NOT EXISTS` (nenhuma proposta ativa de outro). Eu afirmei que o MVCC do Postgres a protegia, porque `create()` trava a linha do pedido. Não protege:
  quem espera uma linha só TRAVADA (a `create()` não atualiza o pedido quando ele já está `PROPOSTO`) não reavalia o `WHERE`, e o `NOT EXISTS` segue com o
  snapshot do início do comando — a proposta nova que commitou durante a espera não era vista e o pedido voltava a `PENDENTE` com uma proposta `ATIVA`
  escondida (`accept()` exige `PROPOSTO`: o cliente não conseguia aceitá-la). Provado em Postgres real (`PENDENTE` em vez de `PROPOSTO`). Conserto: um comando
  à parte, `travarPedidosPropostosDoPrestador` (`SELECT ... FOR NO KEY UPDATE`, em ordem de id), roda ANTES do `UPDATE`; o `UPDATE` vira um comando novo, com
  snapshot novo, e enxerga o que commitou. (O `UPDATE` de `cancelarSemAndamento` da expiração tem o MESMO desenho e eu o havia deixado como "estado consistente, não alterado" — o passo 41 só SIMULAVA
  a corrida, chamando o `UPDATE` com ids escolhidos. A corrida real, com uma proposta nova em voo, foi provada depois (E2E 74, vermelho: o pedido terminou `CANCELADO` por cima
  da proposta que acabava de chegar, que ainda era encerrada junto). Corrigido com o mesmo conserto: `travarPedidos(lote)` (`FOR NO KEY UPDATE`, em ordem de id — a
  consulta dos candidatos passou a ter `ORDER BY s.id`, a mesma ordem da exclusão de conta) num comando à parte antes do `UPDATE`.)
  **Fora do escopo, por decisão de foco:** `accept()` também muda o status do pedido (`PROPOSTO → ACEITO`) e fecha as
  outras propostas ativas, sem adquirir a mesma trava — um `create()` correndo bem no meio de um `accept()` não foi
  endereçado aqui (não é o achado do Codex, é um risco adjacente, de menor probabilidade: a janela é bem mais estreita
  e o pior caso é uma proposta `ATIVA` sobrando num pedido `ACEITO`, não dinheiro perdido).

## 2ª rodada de revisão cruzada (auto-revisão, 2026-10-09)
A correção da 1ª rodada fechou só o `cancel()` (UPDATE guardado). Dez ângulos independentes, cada achado conferido no código, mostraram
que ela cobria um dos nove escritores de `ServiceRequest.status` — e que a minha correção de reabertura tinha um defeito próprio.
- **Medição que decide o desenho:** `findByIdComTrava` sobre uma entidade que a sessão já carregou devolve a MESMA instância com o
  estado ANTIGO (testado contra o Postgres real). Reler "sob a trava" não relê nada; a trava só vale como PRIMEIRA leitura do pedido
  na transação. A `reabrirSeNaoHaPropostaAtiva` da 1ª rodada relia o pedido depois de a checagem de posse já tê-lo carregado, então o
  status conferido podia ser o de antes — um pedido cancelado no meio voltava a `PENDENTE` (E2E 53 reproduz; a mutação o confirma).
- **Escritores corrigidos** (todos leem o pedido com trava, como primeira leitura): `accept`, `reject`/reabertura (o id do pedido sai
  de uma consulta escalar, para não carregar a proposta — e com ela o pedido — antes da trava), `start`, `confirmCompletion`,
  `openDispute`, `MediationService.resolver` e `ServiceRequestService.publicar`. Cada um regravava a entidade inteira: um `cancel()`
  que commitasse no meio era sobrescrito. O caso de dinheiro: `confirmCompletion` sobre um `cancel()` em voo deixava o outbox com
  `PAYMENT_REFUNDED` **e** `PAYMENT_RELEASED` da mesma transação (E2E 51).
- **`accept()` ganhou a checagem de estado** que nunca teve (exige `PROPOSTO`) e serializa com `create()`: uma proposta nova que
  chegasse entre a consulta das `ATIVA` e o aceite ficava órfã num pedido `ACEITO`, e um segundo `accept()` a aceitava também (E2E 52).
- **`confirmPayment` (reconciliação da 1ª rodada) tinha dois defeitos meus:** (1) reentrega do webhook — comum — achava a transação
  `REEMBOLSADA` e estourava `INVALID_PAYMENT_TRANSITION` (422 ao gateway, que reentrega para sempre); (2) com a transação `RETIDA` por
  um `cancel()` que já enfileirou o reembolso, a reconciliação enfileiraria um SEGUNDO `PAYMENT_REFUNDED`. Agora só reconcilia na
  transição `PENDENTE → RETIDO`; qualquer outra confirmação é no-op. Também trava o pedido antes de ler a transação, o que fecha a
  janela em que `cancel()` e a confirmação commitam em paralelo (o `cancel()` não via a transação ainda `PENDENTE`) e serializa
  entregas duplicadas simultâneas (E2E 55).
- **Expiração:** `idsComStatus(lote, CANCELADO)` contava como expirado um pedido que o cliente cancelou entre a consulta e o `UPDATE`
  do job. Agora `idsCanceladosEm(lote, agora)` só devolve o que ESTE `UPDATE` gravou (mesmo `updated_at`, em microssegundos). E2E 54.

**Limites aceitos, não corrigidos:** `PaymentService.initiate`/`criar` lê o pedido sem trava; um cancelamento que commite entre a
leitura e o commit deixa uma transação `PENDENTE` num pedido `CANCELADO`, que a reconciliação acima devolve quando o gateway confirmar
(cobrança e devolução em vez de recusa na origem). (`AccountDeletionRepository.reabrirPedidosSoComPropostaDoPrestador` deixou de ser
um limite: ver a correção da rodada 3 acima — o MVCC NÃO o serializava com quem só segura a linha.)

## Efeito nos testes
`ProposalServiceTest`, `ServiceExecutionServiceTest`, `PaymentServiceTest`, `ServiceRequestExpirationServiceTest`,
`AccountDeletionServiceTest` e o E2E (passo 30 ajustado; passos 39–41 e 45 novos; passo 41 ganhou um bloco extra provando
a guarda contra a proposta nova). `mobile/tests/14-cancelar-pedido-sem-prestador.spec.ts`.

## Ligado a
- US15, US16, US19 e US36 em `docs/spec.md`; `ProposalService`, `ServiceExecutionService`, `ServiceRequestExpirationService`.
- [[exclusao-de-conta-por-anonimizacao]] (onde o problema apareceu).
