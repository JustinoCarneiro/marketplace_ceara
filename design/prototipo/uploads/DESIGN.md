# DESIGN — Marketplace de Serviços Residenciais (Ceará)

> Fonte da verdade visual do projeto (Fase 2a · `onda-direcao-visual`).
> Consumir junto com `design/tokens.css`, `CLAUDE.md` e `ROADMAP.md`.
> **Direção aprovada:** *A · Maré Clara equilibrada* — identidade Onda adaptada para app mobile.
> **Status de propriedade:** produto da própria Onda → uso intencional da marca (a regra de "não usar cores Onda em projeto de cliente" não se aplica).

---

## 1. Conceito

**Confiança calorosa.** O marketplace mexe com dinheiro retido (escrow) e com a casa das pessoas — precisa transmitir **segurança** sem virar banco frio. A base areia + Manrope traz o calor litorâneo da Onda; a **turquesa** marca cada ação; o **oceano** assina os selos de confiança (verificado, valor retido). Acentos quentes (sol, terra) aparecem em doses mínimas para manter o tom humano sem cair no "colorido demais".

Tradução do briefing aprovado:
- **Confiável & seguro** → oceano nos selos (VERIFICADO, RETIDO), contraste AA, hierarquia clara.
- **Ágil & prático** → uma só ação primária por tela (turquesa), squircles, fluxos curtos.
- **Próximo & humano** → fundo areia quente, acentos sol/terra pontuais, fotos de gente real.
- **Acessível & popular** → tipografia grande (corpo 16px+), alvos de toque ≥ 44px, rótulos junto da cor.

Filtros que vieram do "detesta": **nada de** azul de banco engessado, paleta de brinquedo, cara de template ou telas poluídas.

---

## 2. Paleta (comentada)

| Papel | Token | Hex | Uso |
|---|---|---|---|
| Fundo | `--bg` | `#F3ECDC` | areia — fundo de todas as telas |
| Card | `--surface` | `#FCF8EE` | marfim — cards de prestador, sheets |
| Texto | `--text` | `#0E2A33` | corpo e títulos (AA sobre areia/marfim) |
| **Ação / CTA** | `--primary` | `#14A8A0` | **turquesa — uma ação principal por tela** |
| Confiança | `--institutional` | `#0E3F52` | oceano — selos VERIFICADO / valor RETIDO, faixas |
| Acento quente | `--warm-sun` | `#F2B015` | estrelas/rating, pequenos destaques |
| Acento quente | `--warm-terra` | `#DA6A32` | alertas suaves, status de disputa |
| Sucesso | `--success` | `#1B8C84` | concluído, liberado |
| Erro/Disputa | `--danger` | `#C0392B` | cancelamento, falha |
| Borda | `--line` | `#DCD2BC` | divisores |

**Regra de ouro:** areia (fundo) → ink (texto) → **turquesa (1 ação)**. Oceano é institucional (blocos sólidos, selos), nunca CTA. Sol/terra/coral são temperos — pequenas doses, jamais grandes áreas.

**Status do pedido** e **status financeiro (escrow)** têm tokens próprios (`--status-*`, `--escrow-*`) para a máquina de estados do `ROADMAP.md` — sempre acompanhados de **ícone + rótulo** (não comunicar só por cor).

---

## 3. Tipografia

**Manrope** (Google Fonts), família única, pesos 400–800. Geométrica, legível da UI ao display.

| Elemento | Token | Peso | Notas |
|---|---|---|---|
| Hero de tela | `--fs-display` (32px) | 800 | tracking `-0.03em` |
| Título de tela | `--fs-h1` (26px) | 800 | `-0.025em` |
| Seção | `--fs-h2` (21px) | 700 | |
| Título de card | `--fs-h3` (18px) | 700 | nome do prestador / serviço |
| Corpo | `--fs-body` (16px) | 400 | line-height 1.6, cor `--text-soft` |
| Eyebrow / rótulo | `--fs-eyebrow` (12px) | 600 | MAIÚSCULAS, tracking `0.2em`, cor turquesa |

Destaque em título usa **cor turquesa**, nunca itálico. Corpo nunca abaixo de 14px (público popular, leitura confortável).

---

## 4. Componentes-chave (mobile)

- **Botão / CTA:** pílula (`--r-pill`), fundo `--primary`, texto branco, peso 600, altura ≥ 44px, seta opcional à direita. Variante secundária: contorno `--text` (ghost). **Um CTA primário por tela.**
- **Card de prestador:** fundo `--surface`, raio `--r-card` (24px), borda `--line-soft`, `--shadow-soft`. Avatar, nome (h3), `★ nota` em `--warm-sun`, distância, **chip [VERIFICADO]** em oceano, faixa de preço. Hover/press: sobe e ganha `--shadow-float`.
- **Chip de status:** pílula pequena com a cor `--status-*`/`--escrow-*` correspondente + ícone + rótulo. Ex.: `RETIDO` (oceano), `EM ANDAMENTO` (turquesa), `EM DISPUTA` (terra).
- **Selo de confiança:** badge oceano com ícone de escudo — usado em "Prestador verificado" e "Pagamento retido com segurança".
- **Bottom sheet:** raio `--r-sheet` (28px), usado para propostas, escolha de pagamento (Pix/Cartão) e SOS.
- **Botão SOS:** ação de emergência destacada em `--danger`, sempre visível durante pedido `EM_ANDAMENTO`, alvo de toque generoso.
- **Estados de tela:** vazio (busca sem prestadores no raio → ilustração + texto, nunca erro), carregando (skeleton areia), erro (mensagem clara + ação de retry).

---

## 5. Layout & motion

- Escala de espaçamento **8px** (`--space-*`). Gutter mobile 20px.
- Raios generosos (squircles); evitar cantos retos de 0px em conteúdo.
- Sombra é **oceano translúcido**, nunca preto puro.
- Curva de animação `--ease` ("deslizante como água"); tudo desliga com `prefers-reduced-motion` / Reduce Motion.

---

## 6. Acessibilidade (obrigatório)

- Contraste AA: `--text` sobre areia/marfim e branco sobre turquesa/oceano atendem.
- **Nunca** comunicar só por cor — status sempre com ícone + rótulo.
- Áreas de toque ≥ `--touch-min` (44px).
- Sempre prever versão estática para quem reduz movimento.

---

## 7. Do / Don't

| ✅ Do | ❌ Don't |
|---|---|
| Turquesa só para a ação principal | Turquesa como grande área de fundo |
| Oceano em selos de confiança e faixas | Oceano como cor de CTA |
| Acentos sol/terra em pequenas doses | Encher a tela de cores quentes (vira "infantil") |
| Fundo areia quente em tudo | Azul de banco frio dominando (vira "corporativo") |
| Status com cor + ícone + rótulo | Status comunicado só pela cor |
| Corpo ≥ 14px, toque ≥ 44px | Texto miúdo / alvos apertados |
| Fotos de gente real, luz dourada | Foto genérica de banco sem alma |

---

*Documento vivo. Toda decisão de UI volta à pergunta-âncora da Onda:*
**é belo no design, fluido no uso e seguro por dentro?**
