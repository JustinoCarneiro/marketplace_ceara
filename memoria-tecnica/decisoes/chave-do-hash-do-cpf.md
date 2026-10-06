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

## Efeito nos testes
Unitários: `CpfHashServiceTest`, `CpfHashKeyCheckTest` (reescrito: verificador por versão, cruzamento com e sem âncora,
chave trocada por baixo, rollback), `AuthServiceTest` (confirmação com hash de chave antiga, `CPF_MISMATCH`),
`ProviderCpfBackfillTest` (regravação por versão, duplicata ignorando a própria conta), `PaymentServiceTest`. E2E: passo 53
(rotação ponta a ponta contra o Postgres real, com `hash-key-previous` configurado no perfil `e2e` — prova também o
verificador e a recusa de versão desconhecida) e a validação de schema do Hibernate sobre `cpf_hash_versao`.

## Ligado a
- [[cpf-unico-para-o-prestador]], [[conta-unica-com-papeis]];
- `docs/PENDENCIAS_INTEGRIDADE.md` (Camada 2) e `docs/PENDENCIAS_JURIDICAS.md` (base legal do tratamento do CPF).
