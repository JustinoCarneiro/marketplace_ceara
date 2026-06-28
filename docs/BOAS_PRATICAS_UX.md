# 🎨 Boas Práticas de UI/UX — Backlog de Implementação

> **Status:** Pesquisa consolidada em 2026-06-28. Itens marcados ⬜ ainda **não
> implementados** — candidatos para a próxima sessão.
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

- ⬜ **Barra de status do escrow** visível no acompanhamento do pedido:
  `Cliente paga → Retido na Onda → Serviço concluído → Prestador recebe`.
  Hoje temos as telas de estado (`EscrowConfirmedScreen`, `EscrowHeldScreen`)
  mas falta o **stepper contínuo** que mostra ONDE o dinheiro está agora.
  → aplicar em `RequestDetailScreen` (cliente e prestador).
- ✅ Selo de pagamento seguro (já existe na `SplashScreen` — badge "Pagamento
  seguro com escrow").
- ⬜ **Mascaramento de contato** no chat pré-transação (telefone/whatsapp
  borrados) com aviso amigável: "Seus dados ficam protegidos enquanto o
  pagamento está retido". Relevante quando o chat for implementado.
- ⬜ **Custos antecipados sem dark patterns:** mostrar comissão Onda, valor do
  serviço e total na mesma tela, antes de confirmar. Conferir `PaymentChoiceScreen`
  (já mostra "Comissão Onda (10%)" — validar que o total é explícito).

## 2. Onboarding Progressivo

- ⬜ **Pedir o mínimo no cadastro, cobrar dados pesados só na intenção real.**
  Casa diretamente com a [[PENDENCIAS_INTEGRIDADE]] Camada 2: pedir CPF do
  cliente apenas no **primeiro pagamento**, não no cadastro. Cadastro continua
  leve (nome/email/senha), KYC entra no momento de transacionar.
- ✅ Cadastro de cliente já é leve (3 campos).

## 3. Reputação e Avaliações (Épico 7)

- ⬜ **Avaliação double-blind (reveal simultâneo):** nota/comentário só ficam
  visíveis quando **ambas as partes avaliaram** OU o prazo expira. Padrão
  Airbnb. Reduz retaliação e conluio — reforça a [[PENDENCIAS_INTEGRIDADE]].
  → afeta `RateScreen`, `RateConfirmScreen` e a exibição no perfil.
- ⬜ **Avaliação só com transação verificada:** nota só conta se vinculada a um
  `service_request` CONCLUIDO (impede review de não-cliente).
- ⬜ **Canal de denúncia** de avaliação/prestador fraudulento (UI simples de
  "Reportar"). Requer moderação no painel admin (Épico 9).

## 4. Estados de Tela: Loading / Vazio / Erro (consistência)

Princípio: **nunca deixar o usuário adivinhando.** Padronizar os 3 estados em
todas as telas que dependem de rede.

- ⬜ **Skeleton screens** (não spinner) nas listas de carga rápida —
  `HomeScreen` (prestadores próximos), `ResultsScreen`, `MyRequestsScreen`,
  `AvailableRequestsScreen`. Skeleton reduz a percepção de espera e evita
  "pulo" de layout. Spinner só para esperas longas/indeterminadas.
- ⬜ **Estados vazios com orientação** (não tela em branco): "Nenhum prestador
  no seu raio ainda — aumente a distância ou volte mais tarde" + ação. A spec
  já exige isso para `nearby` vazio (US03).
- ⬜ **Erros com retry e mensagem específica:** distinguir erro de rede
  (sem conexão) de erro HTTP de validação (422). Hoje o tratamento é por
  `try/catch` com mensagem genérica em vários lugares (`HomeScreen` engole o
  erro e mostra lista vazia — confunde "vazio" com "falhou").
- ⬜ **Componente compartilhado** `<ScreenState loading|empty|error>` para
  garantir consistência visual entre telas (evita cada tela reinventar).

## 5. Acessibilidade (AA — exigência do projeto)

- ⬜ **Área de toque mínima 48×48 dp** em todos os alvos. Auditar ícones com
  `hitSlop` pequeno e chips. (Já usamos `hitSlop` em vários botões — falta
  padronizar e revisar os menores.)
- ⬜ **Labels acessíveis** (`accessibilityLabel`) em ícones-botão sem texto
  (voltar, olho de senha, SOS, chevrons). Hoje vários `<Feather>` clicáveis não
  têm rótulo para leitor de tela (TalkBack/VoiceOver).
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

## Priorização sugerida para a próxima sessão

| Prioridade | Item | Épico | Esforço |
|-----------|------|-------|---------|
| 🔴 Alta | Barra de status do escrow no `RequestDetail` | 5/6 | Médio |
| 🔴 Alta | Estados Loading/Vazio/Erro padronizados + `<ScreenState>` | todos | Médio |
| 🔴 Alta | Labels de acessibilidade em ícones-botão | todos | Baixo |
| 🟡 Média | Avaliação double-blind | 7 | Médio |
| 🟡 Média | CPF no 1º pagamento (onboarding progressivo) | 1/5 | Médio (back) |
| 🟢 Baixa | Skeleton screens nas listas | 2/4 | Médio |
| 🟢 Baixa | Mascaramento de contato no chat | — | Depende do chat |

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
