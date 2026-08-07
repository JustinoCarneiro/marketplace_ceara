# 🛡️ Pendências de Integridade e Antifraude — Backlog

> **Status:** Camadas 1 e 2 (recomendadas para o MVP) **implementadas e confirmadas em
> 2026-08-07** durante o teste de fluxo ponta a ponta do mobile — ver abaixo. Camada 3 e
> defesas complementares continuam como backlog de v2.
> **Origem:** Levantado em 2026-06-28 durante revisão da tela de Pedidos.

---

## 1. Auto-contratação / Reputação artificial (CRÍTICO)

### O problema
Hoje uma mesma pessoa pode criar **duas contas** (uma cliente, uma prestador) e
usá-las para fabricar histórico e reputação:

1. Cria conta cliente com `email-a@x.com` (cadastro de cliente **não pede CPF**).
2. Cria conta prestador com `email-b@x.com` + CPF real.
3. Como cliente, abre um pedido fictício.
4. Como prestador, aceita o próprio pedido.
5. Paga para si mesmo (Pix para a própria conta bancária).
6. Conclui o serviço e se auto-avalia 5 estrelas.
7. Repete → infla reputação como prestador artificialmente, ganhando ranking
   nas buscas (`providers/nearby` ordena por proximidade + nota).

Impacto: corrompe o ativo mais valioso do marketplace — a **confiança**. Um
prestador com reputação fabricada desloca prestadores legítimos e expõe clientes
reais a um profissional não validado pelo mercado.

### Camadas de defesa (implementar em ordem)

#### Camada 1 — Validação de transação (backend) · esforço BAIXO · ✅ IMPLEMENTADA
`ProposalService.accept()` (`backend/.../proposal/ProposalService.java:52`) bloqueia
com `SELF_HIRE_FORBIDDEN` quando `clienteId == proposal.getPrestadorId()`. Resolve o
caso trivial (conta única dual-role no futuro), mas **não** impede duas contas
distintas da mesma pessoa — só a Camada 2 resolve isso.

#### Camada 2 — CPF único na plataforma · esforço MÉDIO · ✅ IMPLEMENTADA
- `users.cpf_hash` é `UNIQUE` (migration `V9__user_cpf_hash.sql`) — hash determinístico,
  não o CPF em claro (nota LGPD abaixo, já respeitada).
- Uma pessoa = um CPF = uma identidade. A segunda conta com o mesmo CPF é rejeitada
  (`AuthService.verifyIdentity`, 422).
- **Mitigação de atrito confirmada em produção do fluxo**: o cliente não informa CPF no
  cadastro — só no **primeiro pagamento**, via `PaymentChoiceScreen` (modal "Confirme sua
  identidade" acionado pelo erro `IDENTITY_REQUIRED` de `PaymentService`). Onboarding
  continua leve (ver [[boas-praticas-ux]]), CPF só é pedido quando há intenção real de
  transação — exatamente como recomendado abaixo.
- **Nota LGPD:** hash determinístico (HMAC) numa coluna separada `cpf_hash`, CPF em claro
  nunca é indexado — já implementado assim. Alinhar com [[PENDENCIAS_JURIDICAS]] pra
  formalizar a base legal desse tratamento na Política de Privacidade.

#### Camada 3 — Conta única com múltiplos papéis · esforço ALTO · v2
Modelo Uber/Airbnb: uma conta carrega `roles: [CLIENT, PROVIDER]` e alterna de
contexto. Torna a auto-contratação impossível por construção (sempre o mesmo
`userId`). Custa reescrita de auth + onboarding — fora do escopo do MVP.

#### Defesas complementares (v2+)
- **Detecção de colusão:** mesmo dispositivo (device fingerprint), mesma conta
  bancária de origem/destino do Pix, mesmo IP recorrente entre "cliente" e
  "prestador".
- ~~**Avaliação double-blind**~~ — ✅ **IMPLEMENTADA em 2026-08-07** (antecipada da v2):
  a nota só fica visível quando ambas as partes avaliaram ou após expirar o prazo de
  14 dias. Enquanto oculta não entra na `notaMedia` nem no perfil público. Reduz conluio
  (não dá pra combinar nota vendo a do outro) e retaliação. Spec US28, `ReviewService` +
  `ReviewRevealJob`.
- **Reputação verificada por volume real:** ponderar nota por nº de transações
  com clientes distintos; sinalizar prestadores com poucos avaliadores únicos.

### Recomendação para o MVP
| Camada | Implementar no MVP? | Status | Justificativa |
|--------|--------------------|--------|----------------|
| 1 — Validação de transação | ✅ Sim | ✅ Feito | Uma linha no backend, custo zero |
| 2 — CPF único (pedido no 1º pagamento) | ✅ Sim | ✅ Feito | Resolve o problema real, atrito controlado |
| 3 — Conta dual-role | ❌ v2 | — | Mudança arquitetural grande |
| Avaliação double-blind | ✅ Sim (antecipado) | ✅ Feito | Barato e ataca conluio direto |
| Device fingerprint / detecção de colusão | ❌ v2 | — | Refinamento pós-tração |

---

**Responsável:** Marcos (produto) + backend
**Criado em:** 2026-06-28
**Camadas 1 e 2 confirmadas implementadas em:** 2026-08-07 (achado ao investigar o fluxo
completo de pedido→proposta→pagamento do mobile — ver `mobile/tests/02-fluxo-pedido-completo.spec.ts`)
