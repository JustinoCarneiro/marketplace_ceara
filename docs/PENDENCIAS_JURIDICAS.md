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

---

**Responsável pelo alinhamento:** Marcos (produto) + assessoria jurídica  
**Criado em:** Junho 2026
