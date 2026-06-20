---
name: perfil-automacao
description: Perfil arquitetural para automações — integrações entre sistemas, pipelines de dados, bots e webhooks.
---

# Perfil: Automação

## Contexto arquitetural
Pipeline de integração entre sistemas, automação de processos repetitivos, bots ou orquestração de tarefas.

## Stack recomendada
- **Runtime:** Node.js + TypeScript
- **Fila / jobs:** BullMQ + Redis
- **Banco:** PostgreSQL (log de execuções, estado dos pipelines)
- **Agendamento:** node-cron ou BullMQ repeatable jobs
- **HTTP client:** axios ou fetch nativo (Node 18+)
- **Deploy:** Railway ou Fly.io (precisa de processo persistente, não serverless)
- **Monitoramento:** logs estruturados (pino) + alertas no Discord/Slack/e-mail

## Módulos que SEMPRE aparecem
| Módulo | Peso | Risco |
|---|---|---|
| Orquestrador / pipeline core | Grande | **Alto** ← entra primeiro |
| Connectors de entrada (webhook, cron, evento) | Médio | Médio |
| Connectors de saída (API destino, e-mail, notif) | Médio | Médio |
| Log de execução e histórico | Pequeno | Baixo |
| Retry e tratamento de falha | Médio | Alto |
| Painel de monitoramento (opcional) | Médio | Baixo |

## Perguntas críticas no briefing
- O que dispara a automação? (webhook, agendamento, evento de banco, polling)
- Qual o volume? (execuções/hora) — define se precisa de fila ou chamada síncrona basta
- O que acontece quando falha? (retry automático? alerta humano? compensação?)
- As APIs externas têm rate limit? (Mercado Pago, WhatsApp, etc.)
- Há necessidade de idempotência? (mesma mensagem chegar duas vezes não pode duplicar ação)
- Precisa de painel para o cliente monitorar execuções?

## Riscos arquiteturais
- Jobs sem idempotência causam dados duplicados — definir chave de idempotência desde o início
- APIs externas mudam sem aviso — isolar cada integração num connector próprio (fácil de trocar)
- Automação sem log estruturado é impossível de debugar em produção
- Nunca colocar segredos (API keys) no código — usar variáveis de ambiente com dotenv-safe
