/**
 * Design tokens do Marketplace — portado de design/tokens.css
 * Fonte da verdade visual para o app React Native / Expo.
 * NÃO adicionar valores avulsos aqui — toda mudança parte do tokens.css.
 *
 * Disciplina de escala (acordada na normalização da Fase 2b):
 *   Raios   : somente 12 / 24 / 28 / 100
 *   Fontes  : somente a escala fs.* — nada abaixo de 12
 *   Espaços : múltiplos de 4/8
 */

// ─── Cores ──────────────────────────────────────────────────────────────────

export const color = {
  // Superfícies / fundo (areia)
  bg:          '#F3ECDC',
  bgAlt:       '#EAE0CB',
  surface:     '#FCF8EE',
  surface2:    '#F6EEDC',

  // Texto (ink)
  text:        '#0E2A33',
  textSoft:    '#4C636A',
  textFaint:   '#8A989B',
  textOnAccent:'#FFFFFF',

  // Ação / marca
  primary:        '#14A8A0',
  primaryHover:   '#1AC6B6',
  primaryInk:     '#0E7D77',   // turquesa AA para texto (botão ghost)
  institutional:  '#0E3F52',
  institutional2: '#15596E',

  // Acentos quentes
  warmSun:   '#F2B015',
  warmTerra: '#DA6A32',
  warmCoral: '#E07C61',
  accentSky: '#B7DCE3',

  // Bordas
  line:     '#DCD2BC',
  lineSoft: '#E6DDC9',

  // Semânticos
  success: '#1B8C84',
  warning: '#F2B015',
  danger:  '#C0392B',
  info:    '#15596E',

  // Tints + texto AA (pares — usar sempre juntos)
  skyTint:       '#E2EEF2',
  successTint:   '#DDF0EC',
  sunTint:       '#FDF3D6',
  terraTint:     '#F7E3D6',
  terraTintLine: '#E6BFA6',
  dangerTint:    '#FBE6E2',
  successInk:    '#15756E',
  sunInk:        '#B5810A',
  terraInk:      '#C2572A',
  terraInkDeep:  '#9A4A22',
  dangerInk:     '#9A2820',

  // Timeline / ilustração
  primaryRing:    '#B7E5E1',
  sandStroke:     '#B7A788',
  sandStrokeDeep: '#8A7E66',

  // Status do pedido (service_requests)
  statusPendente:   '#8A989B',
  statusProposto:   '#F2B015',
  statusAceito:     '#15596E',
  statusAndamento:  '#14A8A0',
  statusConcluido:  '#1B8C84',
  statusDisputa:    '#DA6A32',
  statusCancelado:  '#C0392B',

  // Status financeiro (escrow)
  escrowPendente:    '#8A989B',
  escrowRetido:      '#15596E',
  escrowLiberado:    '#1B8C84',
  escrowReembolsado: '#DA6A32',

  // Categorias de serviço
  catEletrica:   '#F2B015',
  catHidraulica: '#15596E',
  catLimpeza:    '#1B8C84',
  catPintura:    '#DA6A32',
  catReforma:    '#244C86',
  catJardinagem: '#3C7A4E',
  catGeral:      '#14A8A0',
} as const;

// ─── Tipografia ──────────────────────────────────────────────────────────────

export const font = {
  family: 'Manrope',    // carregar via expo-font no _layout

  weight: {
    regular:  '400',
    medium:   '500',
    semibold: '600',
    bold:     '700',
    black:    '800',
  },

  // Escala mobile-first — nada abaixo de 12 (acessibilidade)
  size: {
    display: 32,   // hero de tela (800, tracking -0.03)
    h1:      26,   // título de tela (800)
    h2:      21,   // seção (700)
    h3:      18,   // título de card (700)
    body:    16,   // corpo (400, lh 1.6)
    bodySm:  14,   // corpo secundário
    caption: 13,   // legendas
    eyebrow: 12,   // rótulos MAIÚSCULAS
  },

  lineHeight: {
    tight:   1.05,
    heading: 1.15,
    body:    1.6,
  },

  tracking: {
    display: -0.03,   // em (aplicar como letterSpacing = size * tracking)
    eyebrow:  0.2,
  },
} as const;

// ─── Espaçamento (escala 8px) ────────────────────────────────────────────────

export const space = {
  1:  4,
  2:  8,
  3: 12,
  4: 16,
  5: 24,
  6: 32,
  7: 48,
  8: 64,
} as const;

// ─── Raios (squircles — somente 12/24/28/100) ────────────────────────────────

export const radius = {
  field:  12,    // inputs, badges
  card:   24,    // cards
  sheet:  28,    // bottom sheets / modais
  pill:  100,    // botões, chips, tags
} as const;

// ─── Elevação (sombra oceano translúcida) ────────────────────────────────────

export const shadow = {
  soft: {
    shadowColor:   '#0E2A33',
    shadowOffset:  { width: 0, height: 12 },
    shadowOpacity: 0.45,
    shadowRadius:  28,
    elevation:     6,           // Android
  },
  float: {
    shadowColor:   '#0E2A33',
    shadowOffset:  { width: 0, height: 22 },
    shadowOpacity: 0.55,
    shadowRadius:  44,
    elevation:     10,
  },
} as const;

// ─── Motion ──────────────────────────────────────────────────────────────────

export const motion = {
  touchMin: 44,         // área mínima de toque AA (px)
  durFast:  180,        // ms
  durBase:  320,
} as const;

// ─── Export agrupado ─────────────────────────────────────────────────────────

const theme = { color, font, space, radius, shadow, motion } as const;

export type Theme = typeof theme;
export default theme;
