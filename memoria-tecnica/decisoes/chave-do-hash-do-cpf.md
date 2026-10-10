---
tipo: decisao
data: 2026-10-05
status: Ativa
---

# Chave própria e versionada para o hash do CPF (separada da chave de cifra)

## Contexto
O hash do CPF (`users.cpf_hash`, HMAC-SHA256, base da unicidade e do antifraude) usava a **mesma** chave que cifra o CPF do
prestador (`CPF_ENCRYPTION_KEY`, AES). Duas finalidades numa chave só:
- a boa prática é **uma chave por finalidade**: quem obtém uma não deveria obter a outra, e cada uma deve poder ser trocada
  sozinha;
- **trocar `CPF_ENCRYPTION_KEY` mudaria todos os hashes sem aviso**: a unicidade deixaria de enxergar as contas existentes.
  E os hashes dos **clientes** não se refazem — só o hash existe, não há CPF para recalcular. Na prática a chave não podia
  ser trocada nunca.

Mexe em segredo de produção e em PII: classe **R2**.

## Decisão
**HMAC com chave própria e versão da chave guardada em cada hash.**

- **Configuração:** `CPF_HASH_KEY` (obrigatória, ≥ 32 caracteres, **diferente** de `CPF_ENCRYPTION_KEY` — a subida recusa
  se forem iguais), `CPF_HASH_KEY_VERSION` (padrão `2`) e, durante uma migração, `CPF_HASH_KEY_PREVIOUS` (a chave que acabou
  de sair; a versão dela é `atual - 1`).
- **`users.cpf_hash_versao`** (V25, padrão `1`): todas as contas existentes ficam na versão 1 — a chave antiga, que é o valor
  que `CPF_ENCRYPTION_KEY` tinha. Contas novas gravam a versão atual.
- **`CpfHashService`** calcula com a chave atual (`hash`), lista os hashes sob os quais um CPF pode já estar gravado
  (`hashesPossiveis`: atual e anterior — é o que a consulta de duplicata usa) e confere um hash gravado contra a chave da
  versão dele (`confere`).
- **Migração das contas:**
  - **prestador:** o CPF existe cifrado, então `ProviderCpfBackfill` (na subida) decifra e regrava o hash com a chave atual;
  - **cliente:** só ele sabe o CPF em claro. No **1º pagamento depois da troca** o app pede a confirmação de identidade de
    novo (`IDENTITY_REQUIRED` também quando o hash está numa versão anterior); a mesma confirmação regrava o hash com a chave
    atual. CPF diferente do já confirmado é recusado (`CPF_MISMATCH`).
- **`CpfHashKeyCheck`** (na subida, antes do backfill): **recusa subir** se houver conta com hash de uma versão que a
  configuração não sabe mais calcular. Esquecer `CPF_HASH_KEY_PREVIOUS` na 1ª subida não dá erro nenhum — a unicidade deixa de
  enxergar essas contas e quem já confirmou o CPF fica sem conseguir confirmar de novo. Num deploy em rolagem a versão anterior
  segue no ar.

## Implantação (produção e demo)
Antes do deploy, no ambiente (Coolify/compose), **sem** apagar nada:
1. `CPF_HASH_KEY` = um segredo novo e aleatório (`openssl rand -base64 32`), **diferente** de `CPF_ENCRYPTION_KEY`;
2. `CPF_HASH_KEY_PREVIOUS` = o valor **atual** de `CPF_ENCRYPTION_KEY` (que continua como está);
3. deploy: V24/V25 rodam, o backfill regrava os prestadores; os clientes migram no próximo pagamento;
4. quando `SELECT count(*) FROM users WHERE cpf_hash IS NOT NULL AND cpf_hash_versao < 2` der 0, **retire**
   `CPF_HASH_KEY_PREVIOUS`. Enquanto houver conta na versão 1, a variável precisa existir.
5. **Primeira subida com a checagem de âncora (decisão do dono, 2026-10-09):** se o banco já tem contas com CPF confirmado numa versão
   (a 1, na migração da separação) e **nenhum prestador com CPF decifrável** nela, a aplicação **recusa subir** e diz qual chave não pôde
   ser provada — o hash de um cliente não se prova (não há CPF em claro para recalcular). Confira o valor da variável apontada
   (`CPF_HASH_KEY_PREVIOUS` na migração = o valor antigo de `CPF_ENCRYPTION_KEY`) e suba **uma vez** com `CPF_HASH_KEY_CONFIRMED=true`;
   na subida seguinte retire a variável. Banco sem nenhuma conta com CPF confirmado (instalação nova) não é afetado.

Banco sem nenhuma conta com CPF confirmado (instalação nova) não precisa de `CPF_HASH_KEY_PREVIOUS`.

## Próxima rotação
Repetir o padrão: a chave atual vira `CPF_HASH_KEY_PREVIOUS`, uma nova entra em `CPF_HASH_KEY` e `CPF_HASH_KEY_VERSION`
sobe uma unidade. **Só rotacionar de novo depois que a contagem acima zerar**: só a versão logo abaixo da atual é reconhecida,
e a subida recusa se sobrar conta mais antiga.

## Consequências / limites aceitos
- **Clientes que já confirmaram o CPF precisam confirmar de novo uma vez**, no 1º pagamento depois do deploy. É o custo de não
  poder recalcular o hash deles; fica no mesmo modal de sempre.
- **Durante a migração a restrição `UNIQUE(cpf_hash)` do banco não cobre o mesmo CPF sob duas chaves** (os hashes são
  textos diferentes): quem garante é a consulta com as duas chaves (`existsByCpfHashIn`), feita em cada cadastro e
  confirmação. A restrição volta a valer sozinha quando todas as contas estão na versão atual.
- A chave antiga continua configurada (como `PREVIOUS`) enquanto houver conta nela — é a mesma chave que cifrava, então o
  risco antigo (uma chave com duas finalidades) só some quando a versão 1 zera e a variável é removida.
- Os segredos vivem só no ambiente; o repositório (público) guarda modelos com placeholders (`*.example`). Nos workflows de CI o
  valor é uma chave de teste fixa, como já era para as demais.
- **Pendência não resolvida (Codex, 2026-10-05, P2):** uma conta reprovada que exclui a conta mantém o hash do CPF (é
  o registro antifraude "não burla o banimento"), mas perde o CPF cifrado — fica fora do backfill e, numa rotação
  futura, não há como regravar esse hash sozinho. Enquanto houver uma conta assim na versão anterior, `CPF_HASH_KEY_PREVIOUS`
  tem de continuar configurada para sempre, travando a rotação seguinte. Precisa de uma decisão de produto (um
  registro antifraude migrável, separado do hash de login, ou um conjunto de chaves versionado maior que duas) antes
  de ter uma correção — não implementado nesta revisão.

## Revisão cruzada (Codex, 2026-10-05) — achados corrigidos nesta branch
- **P1 — `CpfHashKeyCheck` conferia a VERSÃO, nunca se a CHAVE é a mesma que calculou os hashes já gravados.** Trocar
  o VALOR de `CPF_HASH_KEY` sem subir `CPF_HASH_KEY_VERSION`, ou informar um `CPF_HASH_KEY_PREVIOUS` errado (mesma
  versão, chave diferente), subia sem erro nenhum: a restrição `UNIQUE(cpf_hash)` passava a comparar hashes
  calculados com chaves diferentes — que nunca batem — e a unicidade do CPF se furava silenciosamente (uma conta
  nova podia gravar um CPF que já tinha dono, com outro hash). Corrigido com um **verificador por versão**
  (`cpf_hash_key_verificacoes`, V26 — HMAC de uma semente fixa, nunca um CPF de verdade): a 1ª subida de cada versão
  o grava, as seguintes conferem; se não bater, a subida é recusada. Na 1ª subida de uma versão sem verificador
  ainda, cruza contra uma âncora real quando existe uma (um prestador com CPF decifrável já gravado nessa versão) —
  nunca confia às cegas quando há como provar. Também passou a recusar subir se alguma conta tiver hash de uma
  versão MAIOR que a configurada (indício de rollback).
- **P2 — o compose não repassava `CPF_HASH_KEY_VERSION`.** Definir a versão 3 no host deixava o container no padrão
  (2) mesmo assim — hashes novos saíam com a versão errada. Corrigido nos dois compose (`prod`, `homolog`) e
  documentado em `.env.prod.example`.

## 2ª rodada de revisão cruzada (auto-revisão, 2026-10-09)
- **A âncora do `CpfHashKeyCheck` só existe para prestador.** O hash do cliente não se prova (não há CPF em claro para recalcular), então,
  numa 1ª subida de versão sem nenhum prestador decifrável naquela versão, a chave digitada errada era gravada como referência sem nada que
  a desminta — e esse é o estado normal de um piloto com poucos prestadores. Agora, nesse caso, a subida segue, mas com um `WARN` que diz
  quantas contas estão naquela versão e qual variável conferir à mão. **Decidido pelo dono em 2026-10-09:** o aviso virou recusa
  (exigir uma confirmação explícita da chave quando há contas sem âncora) — ver a seção seguinte.
- Comparação do hash do CPF e do verificador em tempo constante (`MessageDigest.isEqual`), como o segredo do webhook já fazia, e
  normalização do CPF por um lugar só (`Cpf.soDigitos`). `CpfHashKeyCheck` roda como `ApplicationRunner`, depois de o servidor já aceitar
  conexões: é uma característica do Spring Boot, a janela é de milissegundos, e o pior caso exige uma rotação mal configurada.
- Eficiência, sem ação (piloto pequeno): as duas contagens por versão rodam em toda subida sobre `users.cpf_hash_versao`, que não tem índice,
  e `comHashNaVersao` não tem `LIMIT`. Vale um `EXISTS`/índice quando a tabela crescer.

## 3ª rodada — decisão do dono (2026-10-09): sem âncora e com contas, a subida é recusada
- **O que mudou:** na 1ª subida de uma versão (sem verificador gravado) que já tem contas com hash mas nenhum prestador com CPF
  decifrável para provar a chave, o `CpfHashKeyCheck` lança `IllegalStateException` **antes** de gravar o verificador (nada vira
  referência) em vez de só avisar. Sem conta na versão (instalação nova, rotação recém-feita) a subida segue sem pedir nada.
- **A saída do operador:** `CPF_HASH_KEY_CONFIRMED=true` (propriedade `cpf.hash-key-confirmed`, repassada nos dois compose) aceita o caso
  UMA vez e grava o verificador; na subida seguinte ele já vale e a variável sai. Deixada ligada, a aplicação avisa a cada subida: ela
  aceitaria em silêncio a 1ª subida sem prova de uma versão futura (uma rotação, um banco restaurado sem a tabela de verificadores).
- **O que a confirmação NÃO cobre:** uma contradição. Âncora que não confere e verificador já gravado que difere continuam recusando mesmo
  com a variável ligada — ela só afrouxa o "não há como provar", nunca o "a prova diz que está errado".
- **Efeito na implantação:** a 1ª subida de um banco com clientes que já confirmaram o CPF na versão 1 e nenhum prestador decifrável nela
  passa a exigir a confirmação (passo 5 de "Implantação"). É a troca consciente de "sobe com aviso" por "não sobe até alguém conferir a chave".
- **Lacuna de teste achada no caminho:** o `application.yml` de teste liga `cpf-backfill.enabled=false` (por causa do H2) e o perfil `e2e`
  herdava isso, então o bean do E2E **nunca** fazia o cruzamento com âncora: a consulta `comHashNaVersao` só tinha rodado contra mocks.
  O passo 69 monta as instâncias à mão com o cruzamento ligado e cobre, no Postgres real, as três situações (sem âncora, âncora que
  confere, âncora que contradiz).
- Continua valendo o limite de que o check roda como `ApplicationRunner`, depois de o servidor já aceitar conexões (janela de milissegundos).

## Efeito nos testes
Unitários: `CpfHashServiceTest`, `CpfHashKeyCheckTest` (reescrito: verificador por versão, cruzamento com e sem âncora,
chave trocada por baixo, rollback), `AuthServiceTest` (confirmação com hash de chave antiga, `CPF_MISMATCH`),
`ProviderCpfBackfillTest` (regravação por versão, duplicata ignorando a própria conta), `PaymentServiceTest`. E2E: passo 53
(rotação ponta a ponta contra o Postgres real, com `hash-key-previous` configurado no perfil `e2e` — prova também o
verificador e a recusa de versão desconhecida) e a validação de schema do Hibernate sobre `cpf_hash_versao`.
Rodada 3: `CpfHashKeyCheckTest` (recusa sem âncora, confirmação do operador, e os limites da confirmação: contradição e verificador
gravado) e E2E passo 69 (as três situações de âncora contra o Postgres real; mutação — recusa trocada por aviso, confirmação vencendo a
contradição — derruba os dois).

## Ligado a
- [[cpf-unico-para-o-prestador]], [[conta-unica-com-papeis]];
- `docs/PENDENCIAS_INTEGRIDADE.md` (Camada 2) e `docs/PENDENCIAS_JURIDICAS.md` (base legal do tratamento do CPF).
