---
name: perfil-ecommerce
description: Perfil arquitetural para projetos de e-commerce — loja virtual, marketplace ou catálogo com checkout.
---

# Perfil: E-commerce

## Contexto arquitetural
Loja virtual ou marketplace com catálogo de produtos, carrinho, checkout e gestão de pedidos.

## Stack recomendada
- **Frontend:** Next.js (App Router) + Tailwind
- **Backend:** Node.js + Fastify ou Next.js API Routes
- **Banco:** PostgreSQL (produtos, pedidos, usuários) + Redis (sessão/carrinho)
- **Pagamento:** Mercado Pago ou Stripe (gateway é módulo Grande — entra primeiro)
- **Storage:** Cloudflare R2 ou S3 para imagens de produto
- **Deploy:** Vercel (front) + Railway ou Render (API + banco)

## Módulos que SEMPRE aparecem
| Módulo | Peso | Risco |
|---|---|---|
| Auth (cadastro, login, recuperação) | Médio | Médio |
| Catálogo (produtos, categorias, busca) | Médio | Baixo |
| Carrinho e sessão | Pequeno | Baixo |
| Checkout + gateway de pagamento | Grande | **Alto** ← entra primeiro |
| Gestão de pedidos (status, histórico) | Médio | Médio |
| Painel admin (CRUD produtos, pedidos) | Médio | Baixo |
| E-mail transacional (confirmação, rastreio) | Pequeno | Baixo |

## Perguntas críticas no briefing
- Vende produto físico ou digital? (impacta: estoque, entrega, download)
- Precisa de variantes? (tamanho, cor → estrutura de SKU)
- Marketplace (múltiplos vendedores) ou loja própria?
- Gateway de pagamento preferido? (Mercado Pago tem checkout pro nativo)
- Precisa de nota fiscal / integração com ERP?
- Volume esperado: quantos produtos? quantos pedidos/dia?

## Riscos arquiteturais
- Concorrência no estoque (two users, last item) → usar transação no banco
- Webhook de pagamento deve ser idempotente
- Imagens de produto precisam de CDN desde o início
