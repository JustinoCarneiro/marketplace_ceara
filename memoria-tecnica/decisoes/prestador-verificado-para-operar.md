---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# Só prestador VERIFICADO opera (propõe, é contratado e inicia serviço)

## Contexto
A aprovação, reprovação e suspensão do admin (`ModerationService`, US25) só gravava
`providers_profile.status_verificacao`. O único lugar que lia o status era a busca por proximidade
(`status_verificacao = 'VERIFICADO'`). Prestador **em verificação, reprovado ou suspenso** continuava
mandando proposta e, aceito, recebendo — o E2E do mobile passava com um prestador recém-cadastrado e
nunca aprovado. A spec (US25) já dizia que o prestador suspenso "não recebe novos pedidos"; o código só
cumpria a metade da busca. O app também promete ao prestador novo que ele "poderá começar a receber
chamados" depois da verificação (`VerificationScreen`).

O problema virou pauta quando o suporte do Mercado Pago pediu os controles de validação de prestadores
(chamado do Money Out): "aprovação manual" não podia ser apresentada como controle enquanto nada a exigia.

## Decisão
`ProviderVerificationGuard` (pacote `provider`) é a regra única, aplicada em três pontos:

| Ponto | Quem | Recusa quando | Mensagem fala com |
|---|---|---|---|
| `ProposalService.create` | prestador | status ≠ `VERIFICADO` | o prestador (em verificação / não aprovado / suspenso) |
| `ProposalService.accept` | cliente | o prestador da proposta deixou de ser `VERIFICADO` | o cliente ("Escolha outra proposta") |
| `ServiceExecutionService.start` | prestador | status ≠ `VERIFICADO` | o prestador |

Erro: `BusinessException("PROVIDER_NOT_VERIFIED")` (HTTP 422, como todo erro de negócio), **inclusive para
prestador sem perfil** (dado inconsistente; mensagem "cadastro não encontrado") — um código só para "não pode
operar", em vez de o cliente da API tratar `PROVIDER_NOT_FOUND` à parte. A recusa vem **antes de qualquer efeito**: nenhuma proposta gravada, o
pedido não vira `PROPOSTO`, as outras propostas não são encerradas.

**Por que o aceite também:** o status pode mudar entre a proposta e o aceite (o admin reprova ou suspende).
Sem isso, o cliente pagaria — e o repasse sairia — para quem o admin já barrou. **Por que o início:** o
mesmo, depois do aceite e do pagamento retido; o prestador barrado não executa, o cliente cancela e é
reembolsado (cancelar vale em `ACEITO`).

## O que ficou de fora, de propósito
- **Listar pedidos disponíveis continua liberado** para quem está em verificação: ele vê o que virá, só não
  propõe. Esconder a lista exigiria outro estado de tela e não protege dinheiro.
- **Confirmar conclusão, cancelar e abrir disputa não olham o status do prestador:** quem decide aí é o
  cliente (ou o admin na mediação); o prestador suspenso não pode travar o dinheiro do cliente.
- **Repasse automático (Money Out, ainda bloqueado):** quando existir, deve exigir `VERIFICADO` também na
  hora de repassar — o repasse manual de hoje já passa por um operador que vê o prestador e a chave.

## Limite assumido: janela de corrida com a suspensão
O guard lê o status **sem trava**. Se o admin suspender entre a leitura e o commit da operação (milissegundos),
essa única operação passa — é indistinguível de ela ter terminado um instante antes da suspensão. A suspensão
vale para tudo o que começa depois do commit dela, e um aceite que escapasse ainda esbarra no `start`. Travar
(`PESSIMISTIC_READ` no perfil durante a transação) custaria contenção em toda proposta/aceite/início e exigiria
teste com duas transações (como o passo 26 do E2E); fica como opção se a moderação virar automática ou o volume
crescer. Apontado como P1 na revisão cruzada (Codex, 2026-10-04); decisão: documentar, não travar.

## Efeito nos testes
- Todo fluxo que cadastra um prestador e o faz propor precisa **aprová-lo antes**: no E2E do backend, pela
  API do admin (`moderarPrestador`); nos Playwright do mobile, `aprovarPrestador()` (API do admin, o mesmo
  caminho do botão "Verificar"); no Maestro, o job **não sobe o profile seed** (não há admin), então o
  `run-maestro.sh` grava a aprovação direto no banco do CI (`UPDATE providers_profile … VERIFICADO`) entre os
  fluxos 03 e 05 — esse caminho só foi exercitado localmente (função isolada contra o Postgres); o job de
  verdade roda no CI.
- O UPDATE do Maestro também grava `updated_at = now()` (o JPA o atualiza em toda alteração do perfil).
- Armadilha: `psql` imprime a etiqueta `UPDATE 1` junto com a linha do `RETURNING`, mesmo com `-tA` — sem
  `-q` a comparação `[ "$linhas" = "1" ]` falha mesmo com a aprovação feita (pego no teste local).

## Ligado a
- US02 e US25 em `docs/spec.md`; `ModerationService`, `ProviderVerificationGuard`.
- Testes: `ProviderVerificationGuardTest`, `ProposalServiceTest`, `ServiceExecutionServiceTest`,
  `E2EFluxoPrincipalTest` (passos 06, 08 e 12), `admin/tests/13-prestador-verificado-para-operar.spec.ts`,
  `mobile/tests/11-prestador-nao-verificado.spec.ts`.
- [[mercadopago-escrow-modelo-de-repasse]] (controles pedidos pelo suporte do Money Out).
