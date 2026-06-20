# Onda · Sistema de Design & Iconografia

> **Guia de marca para designers.** Traduz a identidade visual da Onda (extraída do código da
> landing page) em regras reutilizáveis para criar artes, posts, apresentações e peças em
> qualquer plataforma (Figma, Illustrator, Canva, Photoshop).
>
> **Belo no design. Fluido no uso. Sólido na segurança.** · **v1.2 · Junho/2026**
> Posicionamento de marca: ver o documento de Branding.

---

## 1. Essência da marca

**Onda — Estúdio de Produtos Digitais · Fortaleza, Ceará.** O conceito visual é **"Tropical
Tech"**: a *Marinha* de Aldemir Martins (escola cearense de pintura) traduzida para interface
digital. Alta tecnologia que não é fria — traz o calor, a organicidade e a luz do litoral
nordestino para dentro do software.

| Pilar | Domínio | Tradução visual |
|---|---|---|
| **Belo** | Design | Tipografia elegante, paleta quente, textura, hierarquia clara. |
| **Fluido** | Funcionalidades (a solução funciona) | Layout sem atrito, transições suaves, legibilidade, ritmo. |
| **Impactante** | Segurança | Clareza, precisão e sinais de confiança — solidez que sustenta o impacto. |

**Palavras-chave estéticas:** litoral · luz quente · formas orgânicas · pinceladas · squircles ·
areia + oceano + turquesa · grão de tela · clareza · precisão.

---

## 2. Logotipo / Wordmark

Wordmark oficial — símbolo (anel + onda) + texto "nda" em gradiente. Use sempre os arquivos
oficiais de `assets/brand/`; **não recriar o logo em texto**.

**Gradiente-assinatura:** `#88399A → #C05171 → #ED7735 → #FEA31B` (violeta → coral → laranja →
âmbar). Tokens `--brand-1` a `--brand-4` · variável composta `--grad-brand`.

**Regras de uso:**

- Sobre fundos **escuros**, o wordmark vira branco (`filter: brightness(0) invert(1)`).
- Sobre fundos **claros**, use sempre o wordmark em gradiente original.
- Área de respiro mínima: a altura do símbolo em todos os lados.
- ❌ Não aplicar contorno, sombra dura ou distorção; não recriar em texto.

---

## 3. Sistema de cores

A marca tem **3 modos**. O designer trabalha primariamente no modo **Maré Clara**.

### 3.1 · Maré Clara (padrão) — fundo claro/areia

| Token | Hex | Papel |
|---|---|---|
| `--sand` | `#F3ECDC` | Fundo principal (areia) |
| `--sand-deep` | `#EAE0CB` | Fundo de seção alternada |
| `--card` | `#FCF8EE` | Cartões / superfícies elevadas |
| `--card-2` | `#F6EEDC` | Superfície secundária |
| `--ink` | `#0E2A33` | Texto principal |
| `--ink-soft` | `#4C636A` | Texto secundário |
| `--ink-faint` | `#8A989B` | Texto terciário / legendas |
| `--ocean` | `#0E3F52` | **Cor institucional** — azul-oceano profundo |
| `--ocean-2` | `#15596E` | Azul mar médio |
| `--sky` | `#B7DCE3` | Céu claro |
| `--sea` | `#1B8C84` | Faixa de mar verde-azulado |
| `--turq` | `#14A8A0` | **Cor de DESTAQUE / CTA** — turquesa vibrante |
| `--turq-bright` | `#1AC6B6` | Turquesa brilhante (hover, detalhes) |
| `--sun` | `#F2B015` | Amarelo-sol — acento quente |
| `--terra` | `#DA6A32` | Laranja-terra (duna) |
| `--coral` | `#E07C61` | Coral rosado |
| `--moss` | `#3C7A4E` | Verde-musgo (capim) |
| `--cobalt` | `#244C86` | Azul cobalto |
| `--line` | `#DCD2BC` | Bordas / divisores |
| `--line-soft` | `#E6DDC9` | Bordas sutis |

Cor de ação (`--on-accent`): `#FFFFFF` — texto/ícone sobre superfícies turquesa.

### 3.2 · Maré Noite (dark) & 3.3 · Tela (pictórico)

| Token | Maré Noite | Tela (pictórico) |
|---|---|---|
| `--sand` | `#07242F` | `#ECE2CB` |
| `--card` | `#0C3140` | `#F6EFDD` |
| `--ink` | `#EAF2EE` | `#2A2014` |
| `--ocean` | `#0A3041` | destaque `--terra` `#DA6A32` |

Na Maré Noite, destaque (turquesa) e acento quente (sol) permanecem os mesmos. No modo Tela, a
cor de ação passa a ser o laranja-terra.

### 3.4 · Paleta "Marinha" (assinatura do quadro)

Paleta de apoio para ilustrações, gradientes e composições artísticas:

`#274472` · `#7B5283` · `#C16A6B` · `#E68A4A` · `#F4B64A` · `#1F8980` · `#C9893A`

Gradiente da pincelada-assinatura: `#244C86 → #3C7A4E → #D9663A → #F2B015 → #14A8A0`

### 3.5 · Cores-assinatura por categoria de serviço

| Serviço | Cor (hover dos cards) |
|---|---|
| Produtos & SaaS | `--ocean` `#0E3F52` |
| Apps mobile | `--turq` `#14A8A0` |
| Sites & Landing | `--terra` `#DA6A32` |
| Design & Branding | `--cobalt` `#244C86` |
| E-commerce | `--moss` `#3C7A4E` |
| Automação | `--sun` `#F2B015` |
| Consultoria | `--coral` `#E07C61` |

### 3.6 · Regras de uso de cor

- **Hierarquia:** areia (fundo) → ink (texto) → turquesa (1 ação principal por tela).
- **Turquesa é pontual** — CTAs, links e detalhes; nunca como grande área de fundo.
- **Oceano** é a cor institucional para grandes blocos sólidos (rodapés, faixas).
- Sol/terra/coral são acentos quentes — pequenas doses, nunca competindo com o turquesa.
- Mantenha contraste AA: `--ink` sobre `--sand`/`--card` está sempre OK.

---

## 4. Tipografia

Família única: **Manrope** (fallback `system-ui, sans-serif`), pesos 400–800. Geométrica com
caráter, legível da UI ao display. Disponível no Google Fonts.

**Princípios:**

- **Títulos (display):** 700–800, line-height ~1.05, tracking negativo (`-0.025em` a `-0.04em`).
- **Subtítulos / h3:** 600–700, line-height ~1.1.
- **Destaque em título:** cor de destaque (turquesa ou gradiente), sem itálico.
- **Eyebrow:** 600, 13px, maiúsculas, tracking `0.2em`, cor de destaque, com traço curto antes.
- **Corpo:** 400, 17px, line-height 1.62, cor `--ink-soft`.

| Elemento | Tamanho (fluido) |
|---|---|
| Display gigante (hero imersivo) | `clamp(64px, 14vw, 210px)` |
| Display de seção | `clamp(38px, 5.4vw, 78px)` |
| Título de seção (h2) | `clamp(36px, 5vw, 64px)` |
| Título de card (h3) | `~29px` |
| Fase da jornada (título) | `clamp(48px, 7.6vw, 108px)` |
| Corpo | `15–20px` |
| Eyebrow / rótulos | `12–13px, maiúsculas, tracking 0.2–0.3em` |

Para artes estáticas, ancore nos valores máximos (títulos de 64–108px em peças grandes).

---

## 5. Iconografia

Linear, leve e arredondada — coerente com a leveza "fluida" da marca.

| Propriedade | Valor |
|---|---|
| Grid / viewBox | 24 × 24 |
| Estilo | Contorno (line), sem preenchimento — `fill: none` |
| Traço (stroke-width) | 1.6 (UI/serviço) a 2.2 (setas, ações) |
| Terminações | `stroke-linecap: round` · `stroke-linejoin: round` |
| Cor | `currentColor` (herda do contexto) |
| Tamanho em uso | 16–20px (inline) · 44px (marca de card) |

**Exceção:** o ícone do WhatsApp é o único preenchido, por ser logo de terceiro (verde `#25D366`).

**Princípios de desenho:**

- Cantos arredondados sempre (linejoin round); traço uniforme; geometria orgânica (curvas suaves).
- Metáforas náuticas/litorâneas quando possível (mar, vela, sol, jangada, horizonte).
- Otimização ótica: alinhe ao grid de 24px; ~2px de margem interna.
- ❌ Ícones sólidos (exceto logos de terceiros), misturar line+glyph, sombras/gradientes/3D, traço <1.4px ou >2.4px.

---

## 6. Layout, grid & espaçamento

| Propriedade | Valor |
|---|---|
| Largura máxima de conteúdo | 1280px (`--maxw`) |
| Margem lateral (gutter) | `clamp(20px, 5vw, 76px)` — 20px mobile, 76px desktop |
| Grid de serviços | bento de 6 colunas (cards ocupam 2/3/4 colunas) |
| Padding vertical de seção | `clamp(74px, 11vw, 156px)` |
| Gap padrão entre cards | 16–34px |

**Raios de canto:**

| Token | Valor | Uso |
|---|---|---|
| `--r-card` | 30px | Cartões, imagens, blocos grandes |
| `--r-pill` | 100px | Botões, chips, tags ("pílula") |
| pequeno | 8–20px | Molduras, badges, campos de formulário |

A marca prefere cantos generosos e arredondados ("squircles flutuantes"). Evite cantos retos de
0px em superfícies de conteúdo.

**Breakpoints:**

- **≤1080px:** menu vira hambúrguer; navegação centralizada.
- **≤1000px:** grids colapsam (hero, serviços, projetos, footer → 1–2 colunas).
- **≤620px:** tudo em 1 coluna; serviços viram carrossel horizontal com snap e dots.

---

## 7. Elevação & sombras

| Token | Valor | Uso |
|---|---|---|
| `--shadow-soft` | `0 18px 40px -28px rgba(14,42,51,.45)` | Cartões em repouso |
| `--shadow-float` | `0 30px 60px -34px rgba(14,42,51,.55)` | Hover / flutuantes |

A cor da sombra é azul-oceano translúcido, não preto puro. O spread negativo deixa a sombra
concentrada e elegante. No hover, os cards sobem 6px e ganham a sombra *float*.

---

## 8. Textura & efeitos

- **Grão de tela (film grain):** ruído `feTurbulence` sobre a página. Opacidade 0.42 (Clara) · 0.30 (Noite) · 0.72 (Tela). Em artes: 5–15% para o toque autoral.
- **Vidro (glassmorphism):** `rgba(255,255,255,0.42)` + borda `0.55` + `blur(16px)`. Badges sobre imagens, créditos, tags.
- **Pincelada-assinatura:** linha sinuosa (Bézier) com gradiente da paleta marinha, traço 5px arredondado, anima da esquerda p/ direita.
- **Squircles & blobs:** formas orgânicas e manchas radiais desfocadas (`blur 60px`) como fundo ambiente.

---

## 9. Movimento & animação

| Princípio | Detalhe |
|---|---|
| Curva padrão | `cubic-bezier(.2,.7,.2,1)` — saída suave, "deslizante como água" |
| Reveal ao rolar | elementos sobem 28px + fade-in (1s), em cascata (delays 0.08s) |
| Hover de card | sobe 6px + flood de cor da categoria de baixo p/ cima (0.5s) |
| Jornada | scrollytelling — 5 fases trocam com cross-fade conforme o scroll |
| Acessibilidade | tudo desliga com `prefers-reduced-motion` |

Direção da luz/movimento: do nascer (esquerda/baixo) ao pôr do sol (direita) — metáfora temporal
que guia transições e storyboards.

---

## 10. Componentes-chave

- **Botão (CTA):** pílula (`border-radius:100px`), fundo turquesa, texto branco, peso 600. Padding 14px 26px / 17px 32px. Seta opcional à direita. Variantes `ink` e `ghost`.
- **Cartão de serviço/projeto:** fundo `--card`, raio 30px, borda `--line-soft`, sombra suave. Numeração serifada ("01"), ícone de linha no topo. Hover: flood da cor-assinatura.
- **Chip / Tag:** pílula pequena, contorno `--line`, texto `--ink-faint`, 12px. Ativo: fundo `--ink`, texto areia.
- **Badge sobre imagem:** vidro (blur) + ponto colorido + texto 12px peso 600.

---

## 11. Direção de arte / imagens

- **Inspiração central:** *"Marinha"* (1978), de Aldemir Martins — mar, dunas, sol, horizonte cearense, em pinceladas e cores quentes.
- Preferir ilustração/pintura estilizada a fotografia genérica de banco.
- Quando usar foto: litoral, luz dourada, mar, texturas naturais.
- Emoldurar artes com moldura clara levemente rotacionada (efeito quadro na parede).
- A paleta das imagens deve conversar com a paleta marinha (§3.4).

---

## 12. Voz, idioma & conteúdo

- **Bilíngue:** Português (`pt-BR`, padrão) e Inglês em toda peça-chave.
- **Tom:** equilíbrio entre técnica e arte — passa segurança e qualidade sem arrogância, com calor sem ser piegas. Direto, sem jargão vazio.
- **Mensagem central:** software belo no design, fluido no uso e impactante na segurança — qualidade verificada, não só prometida.
- **Região na entrelinha:** a origem cearense aparece como sotaque, nunca como bandeira.

✅ "Belo no design, fluido no uso, sólido na segurança." ❌ jargão disruptivo vazio e metáfora
náutica exagerada. Localização: **Fortaleza · Ceará · Brasil.**

---

## 13. Acessibilidade (obrigatório)

- Contraste mínimo AA (texto normal ≥ 4.5:1; grande ≥ 3:1). `--ink` sobre claros e branco sobre oceano/turquesa atendem AA.
- Não comunicar só por cor — use ícone/rótulo junto.
- Áreas de toque ≥ 44×44px.
- Em movimento: sempre prever versão estática (`reduced-motion`).

---

## 14. Referência rápida (cheat sheet)

| Item | Valor |
|---|---|
| Cor institucional | Oceano `#0E3F52` |
| Cor de ação / CTA | Turquesa `#14A8A0` (hover `#1AC6B6`) |
| Gradiente assinatura | `#88399A → #C05171 → #ED7735 → #FEA31B` |
| Tipografia | Manrope (400–800), família única · títulos 700–800, tracking -0.025 |
| Raio | Card 30px · Pílula 100px |
| Gutter | 20px (mobile) → 76px (desktop) · Máx. conteúdo 1280px |

---

*Guia vivo: manter coerência com `css/styles.css` e versionar a cada mudança relevante de marca.*
**Belo no design. Fluido no uso. Sólido na segurança.**
