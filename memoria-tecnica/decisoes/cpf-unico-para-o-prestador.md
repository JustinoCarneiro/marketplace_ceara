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
   duplicidade honesta?).

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

## Efeito nos testes
`CpfTest`, `ProviderServiceTest`, `AuthServiceTest`, `ProviderCpfBackfillTest` e o E2E: passo 31 reescrito (o cenário antifraude de
verdade: prestador reprovado exclui a conta e não volta com o mesmo CPF), passos 42 (inválido, duplicado com e sem máscara, cruzando
papéis) e 43 (o backfill alcança os legados). `mobile/tests/15-cpf-unico.spec.ts`.

## Ligado a
- US02 e US36 em `docs/spec.md`; `ProviderService`, `AuthService.verifyIdentity`, `ProviderCpfBackfill`, `shared/Cpf`.
- [[exclusao-de-conta-por-anonimizacao]] (onde o gap apareceu) e `docs/PENDENCIAS_INTEGRIDADE.md` (Camada 2).
