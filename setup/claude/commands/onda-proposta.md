---
name: onda-proposta
description: Gera proposta comercial PDF com identidade Maré Clara a partir de briefing do cliente.
argument-hint: nome-do-cliente
allowed-tools: Read, Write, Bash
---

# Geração de Proposta Comercial

Cliente: **$ARGUMENTS**

## Passo 1 — Carregar contexto de referência

Leia os três arquivos obrigatoriamente antes de qualquer geração:

- `refs/BRANDING.md` — tom de voz, posicionamento, taglines
- `refs/DESIGN_SYSTEM.md` — identidade visual Maré Clara (cores, tipografia, componentes)
- `refs/Metodologia_de_Desenvolvimento_-_Onda.md` — 6 fases, pesos de módulo, prazo calculado

## Passo 2 — Coleta do briefing

Se não foram fornecidos no prompt, pergunte de forma conversacional (uma ou duas perguntas por vez, nunca como formulário):

1. Problema / necessidade — o que o cliente precisa resolver
2. Escopo desejado — funcionalidades, módulos ou entregáveis
3. Prazo estimado — se o cliente já tem expectativa
4. Orçamento disponível (opcional)
5. Contato responsável — nome e cargo do interlocutor

## Passo 3 — Confirmação de escopo

Antes de gerar o HTML, apresente para confirmação:

- Problema entendido em 2–3 linhas
- Módulos identificados com pesos (Pequeno 1–2d · Médio 3–4d · Grande 5–7d)
- Prazo calculado: `Fase 2 (2d ou 4d) + Σ módulos + 2d Fase 5`
- Investimento de referência (só se o usuário forneceu valores)

Aguarde confirmação explícita antes de avançar.

## Passo 4 — Gerar HTML (11 seções obrigatórias)

Seções — nenhuma pode ser omitida:

1. **Capa** — Logo Onda (SVG inline), título "Proposta Comercial", nome do cliente, data, versão v1.0
2. **Sumário Executivo** — Contexto, problema, valor da solução (máx. 1 página)
3. **Entendimento do Problema** — Detalhamento da necessidade levantada
4. **Solução Proposta** — Solução, arquitetura de alto nível, diferenciais
5. **Metodologia Onda-Dev** — As 6 fases aplicadas ao contexto do projeto
6. **Escopo e Entregáveis** — O que está incluso e o que está fora
7. **Cronograma** — Tabela fases × duração × marcos
8. **Investimento** — Tabela por módulo; pagamento padrão 50/50 PIX
9. **Próximos Passos** — CTA claro
10. **Sobre a Onda** — Texto institucional extraído do BRANDING.md
11. **Rodapé** — Validade 30 dias, dados de contato, versão

### Regras técnicas WeasyPrint

```css
@page { size: A4; margin: 20mm 18mm; }
/* altura máxima por página: 296mm */
/* footer: position: absolute; bottom: 32px em container position: relative */
@import url('https://fonts.googleapis.com/css2?family=Instrument+Serif&family=Plus+Jakarta+Sans:wght@400;600;700;800&display=swap');
```

### Tokens Maré Clara obrigatórios

```css
--sand: #F3ECDC;       /* fundo principal */
--sand-deep: #EAE0CB;  /* fundo seção alternada */
--card: #FCF8EE;       /* cartões / superfícies */
--ink: #0E2A33;        /* texto principal */
--ink-soft: #4C636A;   /* texto secundário */
--ocean: #0E3F52;      /* blocos sólidos, rodapé */
--turq: #14A8A0;       /* destaque / CTA */
--turq-bright: #1AC6B6;
--sun: #F2B015;        /* acento quente */
--terra: #DA6A32;      /* laranja-terra */
--line: #DCD2BC;       /* bordas */
--line-soft: #E6DDC9;  /* bordas sutis */
```

### Tipografia

- **Títulos/display:** `Instrument Serif`, 400, tracking `-0.012em` a `-0.022em`, line-height ~0.98
- **Corpo/UI:** `Plus Jakarta Sans`, 400–800, 17px, line-height 1.62, cor `--ink-soft`
- **Eyebrow:** Jakarta 13px, MAIÚSCULAS, tracking `0.2em`, peso 600, cor `--turq`, traço curto antes

### Componentes

- **Botão CTA:** `border-radius: 100px`, fundo `--turq`, texto branco, peso 600
- **Cartão:** fundo `--card`, `border-radius: 30px`, borda `--line-soft`, sombra `0 18px 40px -28px rgba(14,42,51,.45)`
- **Gradiente logo:** `#88399A → #C05171 → #ED7735 → #FEA31B`

## Passo 5 — Salvar e exportar

Verificar se já existe versão anterior:

```bash
ls clientes/$ARGUMENTS/ 2>/dev/null || echo "primeiro arquivo"
```

Salvar o HTML e gerar o PDF:

```bash
# WeasyPrint gera o PDF direto
weasyprint clientes/$ARGUMENTS/proposta-v1.0.html clientes/$ARGUMENTS/proposta-v1.0.pdf
```

Se já existir v1.0, incrementar: v1.1, v1.2, etc.

---

> **Regras de qualidade:**
> - Português brasileiro, tom profissional e consultivo, sem jargões
> - 8 a 15 páginas; zero placeholders visíveis no documento final
> - Sempre Onda-Dev v2.0 — nunca citar Scrum, Kanban ou PMBOK
> - Preços só com aprovação explícita do usuário
> - Tagline: "Belo no design. Fluido no uso. Sólido na segurança."
