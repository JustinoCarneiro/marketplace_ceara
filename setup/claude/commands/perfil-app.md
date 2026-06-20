---
name: perfil-app
description: Perfil arquitetural para aplicações web ou mobile — SaaS, plataforma, produto recorrente.
---

# Perfil: App / SaaS

## Contexto arquitetural
Aplicação com usuários autenticados, dados por conta/tenant, funcionalidade central recorrente.

## Stack recomendada
- **Frontend:** Next.js (App Router) ou React Native (mobile)
- **Backend:** Node.js + Fastify + Prisma
- **Banco:** PostgreSQL (dados relacionais) + Redis (cache, filas, sessão)
- **Auth:** NextAuth.js ou Auth.js (email + OAuth)
- **Fila:** BullMQ + Redis para jobs assíncronos
- **Deploy:** Vercel + Railway / Render / Fly.io

## Módulos que SEMPRE aparecem
| Módulo | Peso | Risco |
|---|---|---|
| Auth (email, OAuth, recuperação, 2FA) | Grande | **Alto** ← entra primeiro |
| Perfil e configurações de conta | Pequeno | Baixo |
| Funcionalidade core (varia por projeto) | Grande | Alto |
| Dashboard / home autenticada | Médio | Baixo |
| Notificações (in-app + e-mail) | Pequeno | Baixo |
| Planos / assinatura (se SaaS pago) | Grande | Alto |
| Admin interno (gestão de contas) | Médio | Baixo |

## Perguntas críticas no briefing
- Multi-tenant? (dados isolados por empresa/conta ou compartilhados?)
- Colaboração em tempo real? (websocket, presença)
- Modelo de cobrança? (freemium, por uso, por assento)
- Precisa de mobile nativo ou web responsiva basta?
- Há dados sensíveis que exigem criptografia em repouso?
- Precisa de audit log (quem fez o quê e quando)?

## Riscos arquiteturais
- Isolamento de dados por tenant deve ser decidido na modelagem — não depois
- Auth é o módulo mais testado por atacantes — rodar `revisor-seguranca` obrigatório
- Features de colaboração (real-time) aumentam complexidade — avaliar se é MVP
