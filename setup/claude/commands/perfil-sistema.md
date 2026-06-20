---
name: perfil-sistema
description: Perfil arquitetural para sistemas internos — gestão, operação, backoffice com múltiplos perfis de acesso.
---

# Perfil: Sistema Interno

## Contexto arquitetural
Sistema operacional interno com múltiplos perfis de usuário, CRUDs, relatórios e regras de negócio complexas.

## Stack recomendada
- **Frontend:** Next.js (App Router) + shadcn/ui + Tailwind
- **Backend:** Node.js + Fastify + Prisma
- **Banco:** PostgreSQL
- **Auth:** NextAuth.js com RBAC (Role-Based Access Control)
- **Relatórios/export:** pdfkit ou xlsx para geração server-side
- **Deploy:** Railway ou Fly.io (precisa de servidor persistente para crons)

## Módulos que SEMPRE aparecem
| Módulo | Peso | Risco |
|---|---|---|
| Auth + RBAC (papéis e permissões) | Grande | **Alto** ← entra primeiro |
| Entidades principais (CRUDs) | Médio | Baixo |
| Dashboard com KPIs | Médio | Baixo |
| Relatórios e exportação (PDF/Excel) | Médio | Médio |
| Histórico / audit log | Pequeno | Baixo |
| Configurações do sistema | Pequeno | Baixo |
| Notificações internas | Pequeno | Baixo |

## Perguntas críticas no briefing
- Quais são os papéis de usuário? (admin, operador, visualizador, etc.)
- Qual a entidade central? (pedido, processo, contrato, ticket…)
- Há workflow com aprovações? (quem aprova o quê)
- Precisa de relatórios customizáveis ou são fixos?
- Há integração com sistemas legados? (ERP, planilha, API externa)
- Quantos usuários simultâneos? (impacta decisão de infra)

## Riscos arquiteturais
- RBAC mal modelado é impossível de consertar sem migração — definir na Fase 3
- Relatórios pesados devem rodar em background (fila) para não travar o servidor
- Audit log deve ser imutável — nunca permitir UPDATE/DELETE nessa tabela
