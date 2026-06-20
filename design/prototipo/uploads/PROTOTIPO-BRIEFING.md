# Briefing de Protótipo — Fase 2b (Claude Design)

> Cole este documento no Claude Design junto com `design/tokens.css` e `design/DESIGN.md`.
> Objetivo: gerar o protótipo estático aprovável do app **mobile** (React Native/Expo), com dados fictícios.
> Telas derivadas das histórias de `docs/spec.md`. Identidade: direção **A · Maré Clara equilibrada**.

---

## Instruções globais para o gerador

- **Plataforma:** app mobile (iOS/Android), telas verticais. Mobile-first.
- **Consumir, não reinventar:** use as variáveis de `tokens.css` para toda cor, tipografia, raio, espaçamento e sombra. Manrope 400–800.
- **Regra de cor:** fundo areia → texto ink → **uma ação turquesa por tela**. Oceano só em selos de confiança/faixas. Sol/terra em doses mínimas.
- **Dois perfis de usuário:** Cliente (`ROLE_CLIENT`) e Prestador (`ROLE_PROVIDER`). Algumas telas têm versão por perfil — está indicado.
- **Acessibilidade (obrigatório):** contraste AA, alvos de toque ≥ 44px, status sempre com **cor + ícone + rótulo** (nunca só cor), corpo ≥ 14px.
- **Para CADA tela, gerar 3 estados quando aplicável:** `carregando` (skeleton areia), `vazio` (ilustração + texto, nunca erro), `erro` (mensagem clara + retry).
- **Dados fictícios:** nomes cearenses, bairros de Fortaleza, serviços residenciais (eletricista, encanador, faxina, pintura, reforma, jardinagem), valores em R$.

---

## Componentes reutilizáveis (definir 1x)

- **CTA primário** — pílula turquesa, texto branco, ≥44px, seta opcional.
- **Botão secundário (ghost)** — contorno ink.
- **Card de prestador** — avatar, nome (h3), `★ nota` em sol, distância, chip `[VERIFICADO]` oceano, faixa de preço, sombra suave.
- **Chip de status** — cor `--status-*`/`--escrow-*` + ícone + rótulo.
- **Selo de confiança** — badge oceano com escudo ("Prestador verificado" / "Pagamento retido com segurança").
- **Bottom sheet** — raio 28px (propostas, pagamento, SOS).
- **Campo de formulário** — raio 12px, borda `--line`, label acima, mensagem de erro abaixo.

---

## Telas por fluxo

### Fluxo 0 — Entrada & Identidade  *(Épico 1 — US01, US02, US12)*
1. **Splash / Onboarding** — logo, proposta de valor curta, escolha **"Sou Cliente" / "Sou Prestador"**.
2. **Login** — e-mail + senha, link "esqueci a senha", erro de credencial. (refresh token é invisível ao usuário).
3. **Cadastro Cliente** — nome, e-mail, senha forte (indicador de força). Estado de erro `e-mail já existe` (422). CTA "Criar conta".
4. **Cadastro Prestador** — dados + **CPF** (com aviso LGPD "armazenado com segurança") + categoria + bio + localização. Após enviar → tela de status **`EM_VERIFICACAO`** ("Estamos verificando seu cadastro").
5. **Status de verificação do Prestador** — estados `EM_VERIFICACAO` (sol), `VERIFICADO` (oceano + selo), `REPROVADO` (danger + orientação).

### Fluxo 1 — Descoberta & Geobusca  *(Épico 2 — US03, US13)*  · perfil Cliente
6. **Home / Busca** — campo de busca, categorias (chips com cor-assinatura), lista/mapa de prestadores **próximos** ordenados por distância. Header com localização atual.
7. **Resultados — lista de prestadores** — cards. **Estado vazio:** "Nenhum profissional no seu raio" (ilustração, não erro). **Carregando:** skeletons.
8. **Filtros** — bottom sheet: raio (km, slider) + **nota mínima** (estrelas). Aplicar/limpar.
9. **Perfil público do Prestador** — avatar, selo VERIFICADO, nota média, avaliações recentes, categorias, faixa de preço, CTA **"Solicitar serviço"**.

### Fluxo 2 — Solicitação Multimídia + IA  *(Épico 3 — US04, US14)*  · perfil Cliente
10. **Abrir pedido (multimídia)** — descrição (texto), anexar **foto** e **áudio** (chips de mídia anexada), categoria, localização. Pedir permissões nativas (Câmera/Microfone).
11. **Assistente de IA** — após anexar, mostra **descrição sugerida + faixa de orçamento** (min–max), **editáveis** antes de confirmar. Rótulo "Sugestão da IA — você confirma".
    - **Estado de fallback (obrigatório):** se a IA falhar/indisponível → banner discreto "Continue preenchendo manualmente", fluxo **não bloqueia**.
12. **Pedido criado** — confirmação, status **`PENDENTE`**, "aguardando propostas".

### Fluxo 3 — Propostas & Orçamentos  *(Épico 4 — US15, US16)*
13. **(Prestador) Pedidos disponíveis** — lista de pedidos `PENDENTE` na sua categoria/raio.
14. **(Prestador) Enviar proposta** — bottom sheet: **valor** + **prazo (dias)** → CTA "Enviar proposta". Erro `pedido indisponível` (422) se já aceito.
15. **(Cliente) Comparar propostas** — lista de propostas (prestador, nota, valor, prazo) com **comparação**; CTA "Aceitar" por proposta. Ao aceitar → demais "encerradas", segue para pagamento.

### Fluxo 4 — Pagamento & Escrow  *(Épico 5 — US05, US06, US07, US18)*
16. **Escolha de pagamento** — bottom sheet **Pix / Cartão**. Resumo: valor + comissão da plataforma. Selo "Pagamento retido com segurança".
17. **Pagamento Pix** — QR Code / copia-e-cola, status "aguardando confirmação".
18. **Pagamento Cartão** — formulário de cartão, status "processando".
19. **Retenção confirmada (escrow)** — `transaction` **`RETIDO`** + pedido **`ACEITO`**: "Seu dinheiro está retido até o serviço terminar." Selo oceano.
20. **(Prestador) Valor retido** — painel do pedido marcado **"pago e retido"** (chip `RETIDO` oceano) → pode iniciar sem risco.
21. **Abrir disputa** — durante `EM_ANDAMENTO`: motivo (texto), confirmação → status **`EM_DISPUTA`** (terra), valor permanece retido.

### Fluxo 5 — Execução do Serviço  *(Épico 6 — US19)*
22. **Detalhe do chamado (timeline de estados)** — mostra a máquina de estados com chips: `PENDENTE → PROPOSTO → ACEITO → EM_ANDAMENTO → CONCLUIDO`. Ações por perfil/estado:
    - Prestador: **Iniciar** (`ACEITO`→`EM_ANDAMENTO`), **Concluir**.
    - Cliente: **Confirmar conclusão** (`EM_ANDAMENTO`→`CONCLUIDO`), **Cancelar** (quando permitido → reembolso).
    - Transição inválida → mensagem 422 amigável.
23. **Meus pedidos (lista)** — versão Cliente e versão Prestador, agrupados por status, com chip de status colorido.

### Fluxo 6 — Avaliação & Reputação  *(Épico 7 — US08, US20)*
24. **Avaliar (bidirecional)** — só após `CONCLUIDO`: **1–5 estrelas** + comentário + foto opcional. Versão Cliente→Prestador e Prestador→Cliente. Tentativa antes de concluir → 422.
25. **Confirmação de avaliação** — "Obrigado, sua avaliação ajuda a comunidade."

### Fluxo 7 — Segurança (Botão SOS)  *(Épico 8 — US21)*
26. **Botão SOS** — visível e acessível durante pedido `EM_ANDAMENTO` (cor `--danger`, alvo grande). Toque → bottom sheet de confirmação ("Acionar emergência?").
27. **SOS acionado** — confirmação: registra **localização** + horário, aciona canal de emergência/contato. Mensagem de que ficou auditável.

---

## Telas de sistema (transversais)
- **Estado de erro genérico** — ilustração + mensagem clara + "Tentar novamente".
- **Sem conexão** — offline amigável.
- **Permissões negadas** (câmera/mic/localização) — explicação + atalho para ajustes.

---

## Prioridade para o protótipo (caminho do dinheiro = crítico)
**P0 (mostrar primeiro):** 1, 6, 7, 9, 10, 11, 15, 16, 19, 22.
**P1:** 3, 4, 13, 14, 20, 24, 26.
**P2:** o restante (estados, status, confirmações).

---

*Ao final, traga qualquer ajuste visual feito no Claude Design de volta para `design/tokens.css` — ele continua sendo a fonte da verdade para a Fase 4.*
