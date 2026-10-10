# ⚠️ Pendências Jurídicas — Revisão Obrigatória Antes do Lançamento

> **Status:** Rascunho interno em uso apenas para homologação/testes.  
> **Bloqueante:** Nenhum usuário real deve criar conta até que estes itens estejam concluídos.

---

## 1. Termos de Uso

**Arquivo atual (rascunho):** `mobile/src/screens/legal/LegalScreen.tsx` → doc `'terms'`

Pontos que precisam de validação jurídica:

- [ ] Identificação completa da pessoa jurídica (razão social, CNPJ, endereço)
- [ ] Definição clara das responsabilidades da plataforma vs. prestador (art. 7º CDC)
- [ ] Política de cancelamento e reembolso detalhada
- [ ] Cláusulas de arbitragem / mediação extrajudicial (opcional)
- [ ] Foro e lei aplicável
- [ ] Validade do aceite eletrônico (registro de IP + data/hora no backend)

---

## 2. Política de Privacidade (LGPD)

**Arquivo atual (rascunho):** `mobile/src/screens/legal/LegalScreen.tsx` → doc `'privacy'`

Pontos que precisam de validação jurídica:

- [ ] Indicação formal do **Encarregado de Dados (DPO)** — nome, e-mail e canal de contato
- [ ] Base legal para cada dado coletado (art. 7º LGPD): consentimento, execução contratual, legítimo interesse
- [ ] Detalhamento dos subprocessadores (gateway de pagamento, serviço de background check)
- [ ] Política de retenção e prazo de exclusão por categoria de dado
- [ ] Processo formal de atendimento a titulares (portal ou e-mail com SLA definido)
- [ ] Comunicação de incidentes (art. 48 LGPD — prazo de 2 dias úteis à ANPD)
- [ ] Política de cookies (se houver versão web)

---

## 3. Registro de Aceite (backend)

- [x] **Concluído em 2026-08-09.** Tabela `terms_acceptance` (`user_id`, `doc_version`,
  `accepted_at` UTC, `ip_address`) gravada em `POST /auth/register/client` e
  `/auth/register/provider`; o backend rejeita o cadastro (422) se `aceitouTermos` não vier
  `true`. `doc_version` atual: `TermsAcceptance.CURRENT_DOC_VERSION` = `"v1-rascunho"` —
  **atualizar essa constante** quando o conteúdo de Termos/Privacidade for validado
  juridicamente (item 1/2 abaixo), para que o aceite antigo não seja confundido com o novo.

---

## 4. Domínio e E-mails

Os rascunhos referenciam placeholders que precisam ser preenchidos antes do lançamento:

- `[razão social a definir]`
- `CNPJ [a definir]`
- `privacidade@[domínio a definir]`
- `[nome a definir]` (DPO)

> **Também trava o repasse automático (2026-09-29):** o Mercado Pago só libera o
> Money Out (`POST /v1/transaction-intents/process`) mediante autorização comercial
> (chamado WCS-52692) e perguntou se a conta de origem é PJ ou PF — hoje é pessoa
> física. O MP não disse que PF é recusada, mas o pedido costuma incluir CNPJ/razão
> social, então a PJ que operar o marketplace provavelmente é necessária. Ver
> `memoria-tecnica/decisoes/mercadopago-escrow-modelo-de-repasse.md`.

---

## 5. Exclusão de conta (US36)

**Implementada em 2026-10-04** por anonimização no lugar (ver
`memoria-tecnica/decisoes/exclusao-de-conta-por-anonimizacao.md`). As escolhas abaixo são **premissas de
engenharia**, não decisão jurídica — confirmar com a assessoria antes do lançamento:

- [ ] **Retenção do histórico anonimizado** (transações, pedidos concluídos, notas das avaliações): hoje **sem
  prazo de expurgo**. Definir o prazo (fiscal/contábil, defesa em disputas) e quem expurga.
- [ ] **CPF cifrado e chave Pix do prestador** são apagados na exclusão, depois do repasse. Se a plataforma, como
  pagadora no Modelo A, tiver de guardar o CPF de quem recebeu (obrigação fiscal), a regra muda: reter o
  `cpf_cifrado` dos prestadores com repasse concluído.
- [ ] **IP do aceite de termos** (`terms_acceptance.ip_address`) fica depois da exclusão como prova do
  consentimento — é dado pessoal. Confirmar base legal e prazo.
- [ ] **Alertas de SOS** (`sos_alerts` e o payload `SOS_TRIGGERED` do outbox: latitude, longitude e `userId`)
  ficam por segurança. Confirmar o prazo; avaliar zerar/arredondar as coordenadas depois de resolvido.
- [ ] **Denúncias** (`denuncias.detalhes`, texto livre do denunciante) ficam para a moderação.
- [ ] **Trilha de auditoria do admin** (`admin_audit_log`) fica (administradores não excluem a conta por este fluxo).
- [ ] **Conta suspensa** não consegue excluir pelo app (o acesso está cortado). Definir o atendimento desse
  pedido pelo suporte e se a retenção antifraude (hash do CPF) se sustenta como base legal.
- [ ] **Pedido de eliminação feito fora do app** (e-mail ao encarregado/DPO): hoje não há fluxo nem prazo (item 2).

---

**Responsável pelo alinhamento:** Marcos (produto) + assessoria jurídica  
**Criado em:** Junho 2026
