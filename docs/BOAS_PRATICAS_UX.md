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
  (spec US31). Avaliação nasce oculta; é revelada quando a contraparte avalia
  ou quando vence o prazo de 14 dias (`ReviewRevealJob`). Enquanto oculta não
  entra na nota média nem no perfil público — senão a média denunciaria a nota.
  `RateConfirmScreen` mostra ao usuário se já está pública ou até quando fica
  oculta. Reforça a [[PENDENCIAS_INTEGRIDADE]] (reduz retaliação e conluio).
- ✅ **Avaliação só com transação verificada:** nota só conta se vinculada a um
  `service_request` CONCLUIDO — já existia. **2026-08-08:** a parte "impede
  review de não-cliente" não existia de verdade — `avaliadorId` nunca era
  conferido contra quem de fato participou (cliente dono ou prestador com
  proposta ACEITA). Qualquer CLIENT/PROVIDER autenticado avaliava pedido de
  terceiros e fabricava reputação. Corrigido em `ReviewService.avaliar()`
  (`FORBIDDEN` se `avaliadorId` não é o cliente/prestador real do pedido).
- ✅ **Canal de denúncia** de avaliação/prestador fraudulento — 2026-08-08.
  `POST /api/v1/denuncias` (tipo `PRESTADOR`/`AVALIACAO`, motivo + detalhes opcionais),
  fila de moderação no admin (`/denuncias`, resolver marca `RESOLVIDA`), alerta operacional
  ao admin (mesma central do SOS/disputa/verificação). Mobile: link "Reportar prestador" no
  topo do `ProviderProfileScreen` e ícone de bandeira em cada avaliação exibida.

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
- ✅ **Distinguir erro de rede vs. erro do servidor** — 2026-08-08.
  `mobile/src/api/errors.ts`: `HttpError` carrega o status; `screenStateError()`
  resolve título/corpo por causa (401/403 → sessão expirada, 5xx → erro no
  servidor, resto → genérico de dados; qualquer coisa que não seja `HttpError`
  — o fetch nunca chegou a ter resposta — → "sem conexão"). Aplicado nas 6
  telas que usam `<ScreenState>` (antes todas diziam "verifique sua conexão"
  mesmo numa sessão expirada ou erro 500).
- ✅ **Skeleton screens** (não spinner) nas listas de carga rápida —
  implementado 2026-08-07 (`mobile/src/components/Skeleton.tsx`,
  `SkeletonProviderCard`/`SkeletonRequestCard`, replicando as dimensões reais
  dos cards para não pular layout). Substitui o spinner do `state="loading"`
  em `HomeScreen`, `ResultsScreen` (variante prestador) e `MyRequestsScreen`,
  `AvailableRequestsScreen` (variante pedido). `<ScreenState>` continua
  cobrindo só `empty`/`error` nessas 4 telas.

## 5. Acessibilidade (AA — exigência do projeto)

- ✅ **Área de toque mínima 48×48 dp** nos alvos isolados de navegação —
  padronizado `hitSlop={14}` nos 16 botões "voltar" ícone-só (22px de ícone +
  28px de hitSlop = 50dp) em todas as telas que têm um. Duas telas
  (`EscrowHeldScreen`, `PaymentCardScreen`) tinham passado batido do audit de
  labels anterior — sem `hitSlop` e sem `accessibilityLabel` nenhum; corrigidas
  junto. **Exceção deliberada, não pendência:** grupos densos e repetidos
  (estrelas de `StarRating`, grid de categorias/chips) mantêm `hitSlop`
  pequeno de propósito — inflar o alvo aí causa toque ambíguo no vizinho, e a
  diretriz AA aceita alvos menores quando o espaçamento entre eles compensa.
- ✅ **Labels acessíveis** (`accessibilityLabel`) em ícones-botão sem texto —
  auditados todos os `TouchableOpacity` com `<Feather>` sem texto acompanhante
  em `mobile/src/screens/`; os 8 botões de "voltar" sem rótulo
  (`NewRequestScreen`, `PaymentPixScreen`, `ProviderProfileScreen` ×2,
  `CompareProposalsScreen`, `AiAssistantScreen`, `EscrowHeldScreen`,
  `PaymentCardScreen` — os 2 últimos achados só nesta rodada) receberam
  `accessibilityLabel="Voltar"` + `accessibilityRole="button"` +
  `accessibilityElementsHidden` no ícone. Os demais ícones clicáveis já tinham
  texto ao lado (ex.: "Foto", "Filtros", "Copiar") ou já eram rotulados.
- ✅ **Erros não só por cor** — 2026-08-08. Auditados todos os `<Text>` de erro
  do app; achados 7 sem ícone nenhum (`RegisterProviderScreen`,
  `OpenDisputeScreen`, `NewRequestScreen`, `RateScreen`, `SendProposalScreen`,
  `CompareProposalsScreen`, e o erro genérico de `RegisterClientScreen`) —
  todos ganharam `Feather name="alert-circle"` ao lado do texto, mesmo padrão
  já usado em `LoginScreen`/`PaymentChoiceScreen`. `components/Input.tsx` (só
  texto, sem ícone) não foi tocado — está órfão, zero import em todo o app.
- ✅ Placeholders que não somem ao digitar (labels fora do input — já é o padrão).
- 🟡 **Contraste AA** — 2026-08-08, auditoria completa (fórmula WCAG, todos os
  pares `*Tint`+`*Ink` do tema + usos de texto sobre `bg`/`surface`). Corrigido
  o que dava pra corrigir sem tocar em cor de marca: 10 lugares em 6 telas
  usavam `color.primary` como cor de **texto** (links, badges) quando o tema
  já tem `color.primaryInk` — "turquesa AA para texto" no próprio comentário
  do token — só que nunca era usado; troca sem nenhuma mudança visual
  perceptível (2.77:1 → 4.69:1). **4 achados exigem mudar o valor de um token
  de marca** (não uso errado — a cor em si falha), então esbarram no design
  congelado (Gate G3): `color.primary` como fundo de botão + texto branco
  (2.94:1, quase todo CTA do app), `color.textFaint` sobre `bg`/`surface`
  (2.53–2.81:1, ~19 arquivos), `sunInk` sobre `sunTint` (3.10:1, badge
  "Elétrica") e `terraInk` sobre `terraTint` (3.61:1, badges reforma/status).
  Decisão do cliente/design, não código — ver ficha técnica completa na
  memória `contraste-aa-2026-08-08`.

## 6. Checkout / Pagamento

- ✅ **Checkout em uma tela**, custos visíveis, autofill — 2026-08-08.
  `PaymentCardScreen` estava **sem campo de nome do titular** (o estado
  `nome` existia, era usado no preview do cartão, mas não tinha `TextInput`
  nenhum — o preview sempre mostrava o placeholder fixo). Adicionado, com
  `autoComplete`/`textContentType` nos 4 campos (nome, número, validade,
  CVV) e validação real (botão só habilita com os 4 campos preenchidos —
  antes dava pra tocar "Pagar" com o formulário inteiro vazio).
- ✅ **Feedback de processamento** durante a chamada ao gateway — 2026-08-08.
  **Achado mais sério que o item em si:** `PaymentCardScreen.pay()` era só um
  `setTimeout` de 1.5s — nunca chamava o backend, nunca checava nada, sempre
  navegava pra "Pagamento retido com segurança" como se tivesse dado certo.
  `PaymentPixScreen` ("Paguei") fazia a mesma coisa, instantâneo. E
  `EscrowConfirmedScreen` **nunca lia os dados reais** — "José Wagner" e
  "R$ 242 RETIDO" eram texto fixo no componente, não vinham de lugar nenhum.
  Ou seja: o app dizia "pagamento confirmado" sem nunca ter confirmado nada.
  Corrigido: `mobile/src/api/pollTransaction.ts` faz polling real de
  `transacao.statusPagamento === 'RETIDO'` (até ~21s, não trava a tela
  indefinidamente — se estourar, avisa e deixa o usuário voltar depois em
  "Meus pedidos" em vez de fingir sucesso); Pix e Cartão usam o mesmo
  helper; `EscrowConfirmedScreen` busca `GET /service-requests/{id}` de
  verdade e mostra prestador/valor reais. Nova suíte E2E
  `tests/03-pagamento-cartao.spec.ts` — `PaymentCardScreen` nunca tinha
  tido cobertura nenhuma antes.

---

## Priorização sugerida (atualizado 2026-08-08)

Os 3 itens 🔴 Alta da rodada anterior já estavam implementados no código real
(achado ao auditar o backlog contra o estado atual do app — mesmo padrão do
que ocorreu com [[PENDENCIAS_INTEGRIDADE]]). Restam os itens abaixo:

| Prioridade | Item | Épico | Esforço | Status |
|-----------|------|-------|---------|--------|
| ~~🔴 Alta~~ | Barra de status do escrow no `RequestDetail` | 5/6 | Médio | ✅ Feito |
| ~~🔴 Alta~~ | Estados Loading/Vazio/Erro padronizados + `<ScreenState>` | todos | Médio | ✅ Feito |
| ~~🔴 Alta~~ | Labels de acessibilidade em ícones-botão | todos | Baixo | ✅ Feito |
| ~~🔴 Alta~~ | Área de toque mínima 48×48dp (16 botões "voltar") | todos | Baixo | ✅ Feito (2026-08-08) |
| ~~🟡 Média~~ | Avaliação double-blind | 7 | Médio | ✅ Feito |
| ~~🟡 Média~~ | CPF no 1º pagamento (onboarding progressivo) | 1/5 | Médio (back) | ✅ Feito |
| ~~🟢 Baixa~~ | Skeleton screens nas listas | 2/4 | Médio | ✅ Feito |
| ~~🟢 Baixa~~ | Distinguir erro de rede vs. validação no `<ScreenState>` | todos | Baixo | ✅ Feito (2026-08-08) |
| ~~🟢 Baixa~~ | Erros não só por cor (texto + ícone) | todos | Baixo | ✅ Feito (2026-08-08) |
| ~~🟢 Baixa~~ | Checkout em uma tela + autofill (`PaymentCardScreen`) | 6 | Baixo | ✅ Feito (2026-08-08) |
| ~~🟢 Baixa~~ | Feedback de processamento do gateway | 6 | Médio | ✅ Feito (2026-08-08) — achado: confirmação inteira era fake |
| 🟡 Média | Contraste AA — cores de marca (`primary`, `textFaint`, `sunInk`, `terraInk`) | todos | Depende do design | 🟡 Decisão do cliente (Gate G3) |
| 🟢 Baixa | Mascaramento de contato no chat | — | Depende do chat | ⬜ Pendente (bloqueado) |

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
