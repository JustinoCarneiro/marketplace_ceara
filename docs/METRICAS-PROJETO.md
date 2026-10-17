# Métricas do Projeto — marketplace-ceara

> Registro de coleta para a **Análise de KPIs de Fechamento** (metodologia OndaDev, Fase 5).
> Padrão e catálogo: `docs/METRICAS-KPI.md`. Prompt da análise: `docs/PROMPT-ANALISE-KPI.md`.
>
> **Regra de dado sensível:** só o *custo/hora interno da empresa* fica fora deste arquivo — no
> sibling privado `<projeto>-docs-privados/` ou informado na hora da análise. Todo o resto
> (datas, valor do contrato, comissão, timesheet, esperas) mora aqui, versionado com o projeto.
>
> ⚠ **2026-09-02:** blocos 1–2 ainda por reconstruir; bloco 3 semeado com a estimativa
> retroativa por proxy de commit — o registro real por sessão **começa a partir daqui**. Panorama
> entre projetos: `onda-starter/historico-projetos/PANORAMA-KPI.md`.

---

## 1. Kickoff — preencher na Fase 0

| Campo | Valor |
|---|---|
| Nome / slug | marketplace-ceara / `<slug-para-o-arquivo-de-analise>` |
| Valor do contrato (bruto) | `<R$ ____>` |
| Moeda | BRL |
| Canal | `<Workana / contrato direto / indicação / ...>` |
| Comissão de plataforma % | `<__% ou n/a>` |
| Taxa de saque % | `<__% ou n/a>` |
| Regime tributário | `<Simples ~6% / MEI fixo / PJ / n/a>` |
| Valor/hora alvo (referência) | `<R$ ___/h>` |
| Custo/hora interno | **não aqui** — ver sibling privado / informar na análise |
| Data — aceite da proposta | `<AAAA-MM-DD>` |
| Data — início do prazo (gatilho contratual) | `<AAAA-MM-DD>` |
| Data — limite contratual | `<AAAA-MM-DD>` |
| Data — entrega real | `<AAAA-MM-DD ou "em curso">` |
| Deploy / hospedagem | `<Vercel / VPS Coolify / ...>` (conta de quem: `<cliente / Onda>`) |

## 2. Peso dos módulos — copiar do `ROADMAP.md` quando existir (Fase 3)

| Módulo | Peso | Dias estimados |
|---|---|---|
| `<M01 ...>` | `<Pequeno/Médio/Grande>` | `<1-2 / 3-4 / 5-7>` |

Σ dias estimados: `<__>`

## 3. Timesheet — uma linha por sessão (Fase 4)

**A partir de 2026-09-02: uma linha por sessão de trabalho** (data · fase 0–5 · horas · nota).
Nunca foi registrado antes — a linha `(est. retroativa)` abaixo é a estimativa por proxy de commit
(sessões separadas por > 2,5 h + 0,75 h/sessão de ramp-up; detalhe por sessão em
`onda-starter/historico-projetos/estimativas-horas-2026-09-02.md`). É baseline, **não** registro.

| Data | Fase (0–5) | Horas | Nota |
|---|---|---|---|
| 2026-10-14 | 4 | 0,2 | Endurecimento do repasse manual: comprovante, chave Pix CPF e alerta de atraso; testes locais e revisão do diff. |
| 2026-10-16 | 4 | ~0,1 (estimativa) | Complemento do PR #37: gravação atômica dos verificadores; regressão vermelho/verde com JPA/H2 e 25 testes direcionados verdes. Revisão cruzada por subagente independente: sem achados P1/P2/P3. |
| 2026-10-16 | 4 | ~0,3 (estimativa) | Cobertura complementar do PR #38: validação de referência/estados/HTTP, rollback e concorrência com JPA/H2, interface com espaços. Mutação de trava em cópia temporária detectada; sem alteração de produção. Resultados e limitação PostGIS no ROADMAP M06; revisão independente sem achados P1/P2/P3. |
| 2026-10-16 | 4 | ~0,3 (estimativa) | Complemento do PR #36: dois E2E de exclusões cruzadas completas, com espera por cadeia de bloqueios e verificação de ambas as contas. Validação isolada: 653 unitários (1 ignorado), 84 E2E PostGIS verdes e seleção antiga detectada por mutação temporária. Sem alteração de produção; diff para revisão. |
| 2026-10-16 | 4 | ~0,4 (estimativa) | Implementação de pagamento via Cartão de Crédito (US05, M06): tokenização PCI-DSS, parcelas (1..12), bandeira, card_last4, migration V30, integração Mercado Pago e stub. Suíte completa com 753 testes unitários/slice verdes, mobile tsc/unit e admin lint/build verdes. |

Σ registrada: **~1,3 h** (inclui estimativas das sessões de 2026-10-16)
Σ **(est. retroativa por proxy de commit)**: **~52 h piso · ~65–75 h central · ~103 h teto** — 111 commits, 28 sessões

## 4. Log de espera / impedimento (Fase 4)

Abrir um episódio sempre que o trabalho **parar por causa externa** (não conta pausa própria).

| Início | Fim | Motivo | O que destrava |
|---|---|---|---|
| `<AAAA-MM-DD>` | `<AAAA-MM-DD ou "aberto">` | `<cliente / terceiro / técnico / interno>` | `<condição p/ retomar>` |

Σ dias em espera: `<__>`   ·   nº de episódios: `<__>`

## 5. Mudanças de escopo (espelho do changelog do `CLAUDE.md`)

| Mudança | Data | Peso equivalente | Aditivo faturado |
|---|---|---|---|
| `<...>` | `<AAAA-MM-DD>` | `<P/M/G>` | `<R$ __ ou R$ 0>` |

---

*Criado na Fase 0. Mantido durante a Fase 4. Consumido pela análise de fechamento (Fase 5),
que gera `docs/ANALISE-PROJETO-<slug>.md`.*
