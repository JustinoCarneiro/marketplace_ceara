# 🎨 Boas Práticas de UI/UX — Backlog de Implementação

> **Status:** Pesquisa consolidada em 2026-06-28. Itens marcados ⬜ ainda **não
> implementados**. Em **2026-08-07**, ao investigar o fluxo completo do mobile
> (ver [[mobile-web-flow-tests]] e [[PENDENCIAS_INTEGRIDADE]]), vários itens
> listados aqui como ⬜ já estavam implementados no código real — corrigidos
> abaixo para ✅. Itens genuinamente pendentes continuam ⬜.
> **Escopo:** marketplace mobile de serviços com escrow + reputação bidirecional.
> Fontes ao final.
>
> ⚠️ Lembrete: o **visual está congelado** (Gate G3, aprovado pelo cliente).
> Estas práticas devem ser aplicadas **sem alterar a identidade visual** já
> aprovada — são refinamentos de comportamento/estado, não de marca. Mudança
> estética a partir daqui custa aditivo de prazo.

---

## 1. Confiança e Transparência (núcleo do nosso produto)

O diferencial do Onda é o **escrow + reputação verificada**. A pesquisa é unânime:
em marketplace de pagamento, confiança não se afirma, se **mostra** com
micro-interações visíveis.

- ✅ **Barra de status do escrow** visível no acompanhamento do pedido:
  `Cliente paga → Retido na Onda → Serviço concluído → Prestador recebe`.
  `EscrowStepper` já implementado em `RequestDetailScreen` (cliente e
  prestador), lado a lado com a timeline de status do pedido.
- ✅ Selo de pagamento seguro (já existe na `SplashScreen` — badge "Pagamento
  seguro com escrow").
- ⬜ **Mascaramento de contato** no chat pré-transação (telefone/whatsapp
  borrados) com aviso amigável: "Seus dados ficam protegidos enquanto o
  pagamento está retido". Relevante quando o chat for implementado.
- ✅ **Custos antecipados sem dark patterns:** `PaymentChoiceScreen` mostra
  valor do serviço, "Comissão Onda (10%)" e o **Total** somado na mesma tela,
  antes de confirmar — validado.

## 2. Onboarding Progressivo

- ✅ **Pedir o mínimo no cadastro, cobrar dados pesados só na intenção real.**
  Implementado e confirmado 2026-08-07 — ver [[PENDENCIAS_INTEGRIDADE]] Camada 2:
  CPF do cliente é pedido só no **primeiro pagamento** (`PaymentChoiceScreen`,
  modal disparado por `IDENTITY_REQUIRED`). Cadastro continua leve
  (nome/email/senha).
- ✅ Cadastro de cliente já é leve (3 campos).

## 3. Reputação e Avaliações (Épico 7)

- ✅ **Avaliação double-blind (reveal simultâneo):** implementado 2026-08-07
  (spec US28). Avaliação nasce oculta; é revelada quando a contraparte avalia
  ou quando vence o prazo de 14 dias (`ReviewRevealJob`). Enquanto oculta não
  entra na nota média nem no perfil público — senão a média denunciaria a nota.
  `RateConfirmScreen` mostra ao usuário se já está pública ou até quando fica
  oculta. Reforça a [[PENDENCIAS_INTEGRIDADE]] (reduz retaliação e conluio).
- ⬜ **Avaliação só com transação verificada:** nota só conta se vinculada a um
  `service_request` CONCLUIDO (impede review de não-cliente).
- ⬜ **Canal de denúncia** de avaliação/prestador fraudulento (UI simples de
  "Reportar"). Requer moderação no painel admin (Épico 9).

## 4. Estados de Tela: Loading / Vazio / Erro (consistência)

Princípio: **nunca deixar o usuário adivinhando.** Padronizar os 3 estados em
todas as telas que dependem de rede.

- ✅ **Componente compartilhado** `<ScreenState loading|empty|error>` —
  implementado (`mobile/src/components/ScreenState.tsx`) e adotado em
  `HomeScreen`, `MyRequestsScreen`, `AvailableRequestsScreen`, `ResultsScreen`,
  `ActiveJobScreen` e `RequestDetailScreen`.
- ✅ **Estados vazios com orientação** e ⬜→✅ **erros com retry**: resolvidos
  onde `<ScreenState>` foi adotado (6 telas acima) — `error` mostra
  "Tentar novamente" (`onRetry`), `empty` mostra ícone + título + corpo
  explicativo em vez de tela em branco.
- 🟡 **Distinguir erro de rede vs. 422 de validação**: ainda genérico —
  `<ScreenState>` hoje só diferencia "vazio" de "erro", não o motivo do erro.
  Refinamento futuro, não bloqueante.
- ✅ **Skeleton screens** (não spinner) nas listas de carga rápida —
  implementado 2026-08-07 (`mobile/src/components/Skeleton.tsx`,
  `SkeletonProviderCard`/`SkeletonRequestCard`, replicando as dimensões reais
  dos cards para não pular layout). Substitui o spinner do `state="loading"`
  em `HomeScreen`, `ResultsScreen` (variante prestador) e `MyRequestsScreen`,
  `AvailableRequestsScreen` (variante pedido). `<ScreenState>` continua
  cobrindo só `empty`/`error` nessas 4 telas.

## 5. Acessibilidade (AA — exigência do projeto)

- ⬜ **Área de toque mínima 48×48 dp** em todos os alvos. Auditar ícones com
  `hitSlop` pequeno e chips. (Já usamos `hitSlop` em vários botões — falta
  padronizar e revisar os menores.)
- ✅ **Labels acessíveis** (`accessibilityLabel`) em ícones-botão sem texto —
  auditados todos os `TouchableOpacity` com `<Feather>` sem texto acompanhante
  em `mobile/src/screens/`; os 6 botões de "voltar" ainda sem rótulo
  (`NewRequestScreen`, `PaymentPixScreen`, `ProviderProfileScreen` ×2,
  `CompareProposalsScreen`, `AiAssistantScreen`) receberam
  `accessibilityLabel="Voltar"` + `accessibilityRole="button"` +
  `accessibilityElementsHidden` no ícone. Os demais ícones clicáveis já tinham
  texto ao lado (ex.: "Foto", "Filtros", "Copiar") ou já eram rotulados.
- ⬜ **Erros não só por cor:** mensagens de erro com texto + ícone, nunca só
  borda vermelha (daltonismo). `RegisterClientScreen` já faz isso no email —
  padronizar nos demais formulários.
- ✅ Placeholders que não somem ao digitar (labels fora do input — já é o padrão).
- ⬜ **Contraste AA** validar pares tint/texto do `theme` com ferramenta
  (a maioria já foi pensada em pares `*Tint` + `*Ink`).

## 6. Checkout / Pagamento

- ⬜ **Checkout em uma tela**, custos visíveis, autofill onde possível
  (`PaymentCardScreen`). Validar que não exige mais passos que o necessário.
- ⬜ **Feedback de processamento** claro durante a chamada ao gateway (estado de
  loading dedicado, não congelar a tela) — alinhado ao princípio de escrow via
  Saga/Outbox (a UI reflete estado de evento, não trava esperando transação).

---

## Priorização sugerida (atualizado 2026-08-07)

Os 3 itens 🔴 Alta da rodada anterior já estavam implementados no código real
(achado ao auditar o backlog contra o estado atual do app — mesmo padrão do
que ocorreu com [[PENDENCIAS_INTEGRIDADE]]). Restam os itens abaixo:

| Prioridade | Item | Épico | Esforço | Status |
|-----------|------|-------|---------|--------|
| ~~🔴 Alta~~ | Barra de status do escrow no `RequestDetail` | 5/6 | Médio | ✅ Feito |
| ~~🔴 Alta~~ | Estados Loading/Vazio/Erro padronizados + `<ScreenState>` | todos | Médio | ✅ Feito |
| ~~🔴 Alta~~ | Labels de acessibilidade em ícones-botão | todos | Baixo | ✅ Feito |
| ~~🟡 Média~~ | Avaliação double-blind | 7 | Médio | ✅ Feito |
| ~~🟡 Média~~ | CPF no 1º pagamento (onboarding progressivo) | 1/5 | Médio (back) | ✅ Feito |
| ~~🟢 Baixa~~ | Skeleton screens nas listas | 2/4 | Médio | ✅ Feito |
| 🟢 Baixa | Mascaramento de contato no chat | — | Depende do chat | ⬜ Pendente (bloqueado) |
| 🟢 Baixa | Distinguir erro de rede vs. validação no `<ScreenState>` | todos | Baixo | ⬜ Pendente |

**Fora deste doc, mas achados no mesmo levantamento:** dois campos coletados na
UI e nunca persistidos — `bio` do prestador (`RegisterProviderScreen`) e
motivo/detalhes da disputa (`OpenDisputeScreen`) — corrigidos ponta a ponta
(migration `V10__dispute_reason.sql`, DTOs, e exibidos na tela de mediação do
admin).

---

## Fontes
- [The Ultimate Guide to Marketplace Design — Gapsy](https://gapsystudio.com/blog/marketplace-ui-ux-design/)
- [Marketplace App UI/UX Best Practices — Lowcode](https://www.lowcode.agency/blog/marketplace-app-ui-ux-design-best-practices)
- [Mobile Payment App UI/UX Best Practices — Dimitrisych](https://dimitrisych.com/mobile-payment-app-ui-ux-best-practices/)
- [10 UX Design Principles for Fintech — Tenet](https://www.wearetenet.com/blog/ux-design-for-fintech)
- [Secure Payment App like Escrow — Kody Technolab](https://kodytechnolab.com/blog/secure-payment-app-development-like-escrow/)
- [Mobile App Accessibility Best Practices — AFixt](https://afixt.com/mobile-app-accessibility-best-practices-for-inclusive-design/)
- [WCAG 2.2 para Mobile (W3C)](https://www.w3.org/TR/wcag2mobile-22/)
- [Double-blind reviews — Sharetribe](https://www.sharetribe.com/marketplace-glossary/double-blind-reviews/)
- [Marketplace Fraud: Two-Sided Verification — Didit](https://didit.me/blog/marketplace-fraud-two-sided-verification-advanced-sw/)
- [Loading, Error & Empty States in React — LogRocket](https://blog.logrocket.com/ui-design-best-practices-loading-error-empty-state-react/)
- [Skeleton Loading em React Native — OneUptime](https://oneuptime.com/blog/post/2026-01-15-react-native-skeleton-loading/view)

**Criado em:** 2026-06-28
