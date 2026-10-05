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

## Efeito nos testes
`ProposalServiceTest`, `ServiceExecutionServiceTest`, `ServiceRequestExpirationServiceTest`, `AccountDeletionServiceTest` e o
E2E (passo 30 ajustado; passos 39–41 novos). `mobile/tests/14-cancelar-pedido-sem-prestador.spec.ts`.

## Ligado a
- US15, US16, US19 e US36 em `docs/spec.md`; `ProposalService`, `ServiceExecutionService`, `ServiceRequestExpirationService`.
- [[exclusao-de-conta-por-anonimizacao]] (onde o problema apareceu).
