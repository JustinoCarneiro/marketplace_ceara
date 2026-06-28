# 🛡️ Pendências de Integridade e Antifraude — Backlog

> **Status:** Documentado, **não implementado**. Priorizar antes do lançamento com pagamento real.
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

#### Camada 1 — Validação de transação (backend) · esforço BAIXO
Bloquear no endpoint de aceite de proposta que `clientId == providerId` do mesmo
pedido. Resolve o caso trivial (conta única dual-role no futuro), mas **não**
impede duas contas distintas da mesma pessoa.
```java
// pseudo — AceitarPropostaService
if (request.getClientId().equals(proposal.getProviderId())) {
    throw new BusinessException("Prestador não pode aceitar pedido próprio");
}
```

#### Camada 2 — CPF único na plataforma · esforço MÉDIO · **recomendada para o MVP**
- Adicionar CPF também ao **cliente**, com constraint `UNIQUE` na tabela `users`
  (CPF criptografado em repouso, mas com hash determinístico/índice para o
  unique — ver nota LGPD abaixo).
- Uma pessoa = um CPF = uma identidade. A segunda conta com o mesmo CPF é
  rejeitada (422).
- **Mitigação de atrito:** não pedir CPF no cadastro inicial do cliente — pedir
  no **primeiro pagamento** ("Confirme sua identidade para pagar com segurança").
  Mantém o onboarding leve (ver [[boas-praticas-ux]] — onboarding progressivo) e
  só cobra o dado quando há intenção real de transação.
- **Nota LGPD:** o `UNIQUE` sobre dado criptografado exige hash determinístico
  (ex: HMAC-SHA256 com chave do servidor) numa coluna separada `cpf_hash`. O CPF
  em claro nunca é indexado. Alinhar com [[PENDENCIAS_JURIDICAS]].

#### Camada 3 — Conta única com múltiplos papéis · esforço ALTO · v2
Modelo Uber/Airbnb: uma conta carrega `roles: [CLIENT, PROVIDER]` e alterna de
contexto. Torna a auto-contratação impossível por construção (sempre o mesmo
`userId`). Custa reescrita de auth + onboarding — fora do escopo do MVP.

#### Defesas complementares (v2+)
- **Detecção de colusão:** mesmo dispositivo (device fingerprint), mesma conta
  bancária de origem/destino do Pix, mesmo IP recorrente entre "cliente" e
  "prestador".
- **Avaliação double-blind:** nota só fica visível quando ambas as partes
  avaliaram, ou após expirar o prazo — reduz conluio e retaliação (ver
  [[boas-praticas-ux]]).
- **Reputação verificada por volume real:** ponderar nota por nº de transações
  com clientes distintos; sinalizar prestadores com poucos avaliadores únicos.

### Recomendação para o MVP
| Camada | Implementar no MVP? | Justificativa |
|--------|--------------------|----------------|
| 1 — Validação de transação | ✅ Sim | Uma linha no backend, custo zero |
| 2 — CPF único (pedido no 1º pagamento) | ✅ Sim | Resolve o problema real, atrito controlado |
| 3 — Conta dual-role | ❌ v2 | Mudança arquitetural grande |
| Double-blind / device fingerprint | ❌ v2 | Refinamento pós-tração |

---

**Responsável:** Marcos (produto) + backend
**Criado em:** 2026-06-28
