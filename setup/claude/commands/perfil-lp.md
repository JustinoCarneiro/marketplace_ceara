---
name: perfil-lp
description: Perfil arquitetural para sites institucionais e landing pages — foco em conversão e performance.
---

# Perfil: Site / Landing Page

## Contexto arquitetural
Site institucional ou landing page focado em conversão, SEO e carregamento rápido.

## Stack recomendada
- **Frontend:** Next.js (static export ou ISR) + Tailwind
- **CMS:** Payload CMS ou Sanity (se o cliente precisa editar conteúdo)
- **Formulários:** React Hook Form + envio via API Route ou Resend (e-mail)
- **Analytics:** Vercel Analytics + Google Tag Manager
- **Deploy:** Vercel (edge, CDN global, HTTPS automático)
- **Banco:** Só necessário se houver CMS ou formulários com persistência

## Módulos que SEMPRE aparecem
| Módulo | Peso | Risco |
|---|---|---|
| Layout estático (todas as seções) | Médio | Baixo |
| Formulário de contato / lead | Pequeno | Baixo |
| SEO (meta, OG, sitemap, robots) | Pequeno | Baixo |
| Analytics e rastreio de conversão | Pequeno | Baixo |
| CMS (se cliente edita conteúdo) | Médio | Médio |
| Página de blog / artigos (se houver) | Médio | Baixo |

## Perguntas críticas no briefing
- Cliente vai editar o conteúdo? (sim → CMS; não → conteúdo hardcoded)
- Tem blog ou área de artigos?
- Quais conversões medir? (clique no CTA, envio de formulário, ligação)
- Tem múltiplos idiomas?
- Precisa integrar com CRM (HubSpot, RD Station)?
- Domínio e hospedagem já têm provedor definido?

## Riscos arquiteturais
- Performance é critério de aceite — Core Web Vitals (LCP < 2.5s, CLS < 0.1)
- Formulários sem CAPTCHA viram alvo de spam — usar Turnstile (Cloudflare) ou hCaptcha
- CMS mal escolhido trava o cliente no longo prazo — preferir headless com API aberta
