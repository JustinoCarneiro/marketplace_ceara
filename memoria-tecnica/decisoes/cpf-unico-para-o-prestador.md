---
tipo: decisao
data: 2026-10-04
status: Ativa
---

# Uma pessoa = um CPF também para o prestador: valida os dígitos, grava o hash e recusa duplicata

## Contexto
O antifraude Camada 2 (`docs/PENDENCIAS_INTEGRIDADE.md`) diz "uma pessoa = um CPF", mas só o **cliente** tinha o hash do CPF
(`users.cpf_hash`, gravado no 1º pagamento). O cadastro de **prestador** guardava só o CPF cifrado no perfil: não gravava o
hash e não consultava duplicata. Consequências achadas ao implementar a exclusão de conta (US36):
- um prestador **reprovado** podia se recadastrar com o mesmo CPF (com ou sem excluir a conta antes);
- a retenção do hash na exclusão ("excluir não burla o banimento") não tinha efeito para prestadores, porque não havia hash;
- o CPF de um prestador podia virar uma conta de cliente (a auto-contratação por duas contas da mesma pessoa) — fechado pela
  conta única, que torna a auto-contratação impossível por construção.
Além disso **nada validava os dígitos verificadores**: com unicidade por hash, um número inventado a burla.

## Decisão
1. **Cadastro de prestador** (`ProviderService.register`): valida o CPF (`INVALID_CPF`, 422), grava `users.cpf_hash` e recusa CPF
   já vinculado a **qualquer** conta (`CPF_ALREADY_REGISTERED`, 422), inclusive o de um cliente. O hash é de só os dígitos
   (`CpfHashService`): a mesma pessoa tem uma identidade só, com ou sem máscara.
2. **`verify-identity` do cliente** passa a validar os dígitos também. O validador é um só (`shared/Cpf`, extraído do `PixKey`).
3. **Backfill** (`ProviderCpfBackfill`, `ApplicationRunner`, na subida): prestadores já cadastrados só têm o CPF cifrado; ele o
   decifra, grava o hash e a unicidade passa a valer para eles. Idempotente; cada prestador é uma transação, e um registro ruim
   (CPF que não decifra — o seed grava um placeholder —, duplicata, corrida na restrição única) não derruba os outros. Desliga
   com `marketplace.cpf-backfill.enabled=false`.
4. **Duplicata legada não se resolve sozinha**: quando o mesmo CPF está em duas contas antigas, a segunda fica sem hash e vai
   listada no log **só pelo id** (o CPF nunca vai para o log), para decisão humana (qual conta é a verdadeira? é fraude ou
   duplicidade honesta?). O perfil dela é marcado `cpf_conciliado = false` (V27) e o guard de verificação recusa operar —
   ver "Revisão cruzada" abaixo.

**Regra de produto: uma pessoa = um CPF, em todo o sistema** — e, desde 2026-10-05, **uma conta só** (cliente e prestador no
mesmo `user_id`: [[conta-unica-com-papeis]]). A primeira versão desta decisão assumia que o prestador não podia ter conta de cliente;
isso o impedia de contratar, e a alternativa intermediária (CPF único por papel) foi descartada em favor da conta única. O hash
passou a ter chave própria e versionada: [[chave-do-hash-do-cpf]].

## Consequências
- Quem hoje tem as duas contas com o mesmo CPF (se existir) não será barrado retroativamente: o backfill só preenche o hash do
  prestador se o CPF ainda não estiver vinculado; a duplicata aparece no log.
- Testes e fluxos que usavam CPF "só no formato" quebraram e foram corrigidos: o `fakeCpf` do mobile e o `cpfNovo` do admin passam
  a gerar dígitos verificadores certos e únicos; CPFs fixos repetidos viraram derivados do tempo; o fluxo Maestro de cadastro de
  prestador usa `123.456.789-09`. **Quem escrever novo fluxo de cadastro de prestador precisa de CPF válido e único.**
- Um CPF digitado com erro de dígito agora é recusado no cadastro (antes passava e travava no primeiro repasse ou não era notado).
- **O hash de um prestador legado só aparece depois de uma subida da aplicação** com o backfill ligado; até lá, a unicidade não o
  alcança.
- Não valida titularidade (que o CPF é da pessoa): só que existe e é único. Titularidade é o background check (hoje stub).

## Revisão cruzada (Codex, 2026-10-05) — achados corrigidos nesta branch
- **P1 — duplicata legada ainda contratava a si mesma.** O backfill deixava a conta duplicada sem hash, mas o perfil
  `VERIFICADO` continuava apto a propor — o self-hire (`ProposalService.create`) só compara IDs de conta diferentes,
  nunca enxerga que é a MESMA pessoa por trás de duas contas com o mesmo CPF (exatamente o cenário da auto-contratação
  que a conta única resolveu para contas NOVAS, mas não para duplicatas de ANTES dela). O cliente podia aceitar, pagar
  e avaliar essa proposta — reputação e dinheiro fabricados sem ninguém de verdade do outro lado. Corrigido: o backfill
  marca o perfil duplicado (`providers_profile.cpf_conciliado = false`, V27) e `ProviderVerificationGuard`
  (`exigirVerificado`/`exigirContratavel`) passa a recusar operar mesmo com o perfil `VERIFICADO` — sem suspender a
  conta sozinho, a decisão de qual conta é a verdadeira continua humana. Prova no E2E, passo 56.
- **P2 — corrida por CPF devolvia erro interno (500).** Dois cadastros com o mesmo CPF podiam passar pela consulta de
  duplicata antes de qualquer gravação; a restrição `UNIQUE` impedia a dupla, mas a 2ª transação caía sem tradução.
  Corrigido traduzindo a violação dessa restrição (`users_cpf_hash_key`) para `CPF_ALREADY_REGISTERED`/422 em
  `ErrorControllerAdvice`.
- **P3 — a busca podia voltar vazia havendo outro prestador próximo.** `DiscoveryService` excluía o próprio usuário
  DEPOIS da consulta a `ProviderProfileRepository.findNearby`, já com o `LIMIT` aplicado: com `limite=1`, se ele fosse
  o resultado mais próximo, o filtro o removia e não trazia o 2º. Corrigido excluindo na própria consulta nativa
  (`WHERE pp.user_id <> :quemBusca`), antes do `LIMIT`. Prova no E2E, passo 55.

## 3ª rodada (2026-10-10) — o backfill de subida e a justificativa do painel
- **O backfill lia a conta SEM trava e a regravava inteira (E2E 75, vermelho):** uma exclusão de conta que commitasse entre a leitura e o `save` era desfeita — e-mail,
  nome e `excluido_em` voltavam ao valor antigo — e a conta excluída ainda ganhava um hash de CPF. Agora `vincular` lê com `findByIdComTrava` como primeira leitura, ignora
  conta excluída, e `marcarCpfNaoConciliado` roda na mesma ordem do `ModerationService` (conta travada primeiro, perfil depois). Só roda na subida, mas a subida de uma instância
  nova pode coincidir com tráfego da antiga num deploy em rolagem.
- **A `justificativa` do `moderate` tinha tamanho ilimitado** e ia inteira para o log de auditoria (TEXT) e para a página de Auditoria: agora `@Size(max = 500)` no backend e
  `maxLength` nos dois campos da tela (o teste do backend cai sem a anotação).

## 2ª rodada de revisão cruzada (auto-revisão, 2026-10-09)
- **A marca `cpf_conciliado = false` era permanente e não tirava o perfil da busca.** O guard barrava proposta e aceite, mas o prestador
  duplicado continuava aparecendo para o cliente como VERIFICADO — e o cliente caía num `PROVIDER_NOT_VERIFIED` sem entender —, e só um
  `UPDATE` manual no banco desfazia a marca. Agora a busca (`findNearby`) filtra `cpf_conciliado = TRUE` e há a ação de moderação
  `CONCILIAR_CPF` (`POST /admin/providers/{id}/moderate`) para o suporte decidir a duplicata. **No painel (2026-10-10):** a lista do admin passou a expor
  `cpfConciliado` (`ProviderAdminDto`); o perfil do prestador mostra o aviso "CPF em duplicidade" com o botão "Conciliar CPF" (justificativa
  obrigatória na tela, vai para o log de auditoria como `MODERAR_PRESTADOR`) e a lista ganha o filtro e o selo "CPF duplicado" — sem o filtro a
  duplicata, que costuma estar VERIFICADA, ficava escondida atrás da aba padrão "Em verificação". E2E 68 (lista do painel antes e depois);
  admin `16-conciliar-cpf.spec.ts` (tela) e contrato em `09-contratos-api`.
- **Prestador com CPF cifrado que não decifra nunca é conferido.** O backfill o ignora em toda subida (não tem como saber se o CPF dele colide
  com o de um cliente), então a marca de duplicata nunca é posta para ele — e só a CONTAGEM saía no log. Agora os ids saem em `WARN` (nunca o
  CPF). Não foi bloqueado de operar: o seed da demo grava um placeholder que não decifra, e bloquear por ausência de hash quebraria a demo.
  Quem não decifra continua fora da unicidade até alguém conferir à mão.

## Efeito nos testes
`CpfTest`, `ProviderServiceTest`, `AuthServiceTest`, `ProviderCpfBackfillTest`, `ProviderVerificationGuardTest` (novo: CPF
não conciliado), `DiscoveryServiceTest` (exclusão na consulta, não depois), `ErrorControllerAdviceTest` (corrida por CPF) e
o E2E: passo 31 reescrito (o cenário antifraude de verdade: prestador reprovado exclui a conta e não volta com o mesmo
CPF), passos 42 (inválido, duplicado com e sem máscara, cruzando papéis), 43 (o backfill alcança os legados), 55 (busca com
limite=1) e 56 (duplicata legada não contrata a si mesma). `mobile/tests/15-cpf-unico.spec.ts`.

## Ligado a
- US02 e US36 em `docs/spec.md`; `ProviderService`, `AuthService.verifyIdentity`, `ProviderCpfBackfill`, `shared/Cpf`,
  `ProviderVerificationGuard`, `DiscoveryService`.
- [[exclusao-de-conta-por-anonimizacao]] (onde o gap apareceu) e `docs/PENDENCIAS_INTEGRIDADE.md` (Camada 2).
