# Marketplace de Serviços Residenciais (Ceará)

Plataforma hiperlocal que conecta clientes a prestadores de serviços residenciais com pagamento retido (Escrow) e reputação verificada.

## Stack
- Backend: Java 21 + Spring Boot 3.x (Virtual Threads), Spring Security (JWT + refresh token).
- Frontend: React Native + Expo (EAS Build), React Navigation, Context API (sessão) + Zustand (fluxos).
- Persistência: PostgreSQL + **PostGIS** (geobusca).
- Deploy: Docker Compose · GitHub Actions (back) · EAS Build (mobile).

## Perfil de projeto
App mobile marketplace · Cliente + Prestador + (Admin/Mediação) · Serviços residenciais, lançamento hiperlocal por bairro.

## Princípios (não-funcionais críticos)
- **Escrow nunca em `@Transactional` sobre o gateway.** Cobrança/repasse externos via **Saga + Outbox + idempotência** e reconciliação por webhook. O estado financeiro é dirigido por eventos confirmados, não por transação de banco.
- **Geobusca por PostGIS** (índice espacial GiST), não haversine em SQL puro. SLA de busca `nearby` < 300ms (p95).
- **LGPD:** CPF e dados sensíveis criptografados em repouso; mínimo necessário trafegado; DTOs (Records) nunca expõem entidades.
- **Auth:** JWT de validade curta + **refresh token**; roles `ROLE_CLIENT`, `ROLE_PROVIDER`, `ROLE_ADMIN`.
- **IA com fallback manual obrigatório:** se a IA falhar/indisponível, o usuário conclui o pedido manualmente. IA nunca é caminho crítico bloqueante.
- **Idempotência** em todo endpoint que move dinheiro ou cria pedido (chave de idempotência por requisição).
- Latência alvo por endpoint (não global); telas dependentes de integração externa têm SLA próprio.

## Épicos
1. **Gestão de Identidade e Verificação** — cadastro Cliente/Prestador, CPF + background check assíncrono.
2. **Descoberta e Geobusca** — lista por categoria e proximidade (PostGIS), filtros básicos.
3. **Solicitação Multimídia + IA** — pedido por texto/áudio/foto; IA sugere descrição e orçamento (com fallback manual).
4. **Propostas e Orçamentos** — prestador envia proposta de preço; cliente aceita (versão simples do "leilão", sem lances em tempo real).
5. **Pagamento e Escrow** — Pix/Cartão, retenção, split na conclusão, fluxo de disputa (Saga/Outbox).
6. **Execução do Serviço** — máquina de estados do chamado.
7. **Avaliação Bidirecional e Reputação** — Cliente↔Prestador, 1–5 estrelas + comentário/imagem.
8. **Segurança do Usuário (Botão SOS)** — acionamento de emergência durante atendimento.
9. **Administração e Mediação (painel web)** — dashboard de métricas, mediação de disputas, moderação de prestadores, gestão de usuários/categorias, reconciliação financeira, **exportação de relatórios (CSV/PDF)** e **alertas operacionais** (SOS, disputa, verificação inconclusiva). Acesso `ROLE_ADMIN`. **Superfície web** (React), separada do app mobile.

> Receita do MVP: **somente comissão** (10–25%) sobre serviço concluído. Assinaturas, boost e taxa de urgência → v2.

## Máquina de estados principal (`service_requests`)
```
PENDENTE ──(prestador envia proposta)──► PROPOSTO
PROPOSTO ──(cliente aceita + paga/escrow)──► ACEITO
PROPOSTO ──(cliente recusa / expira)──► CANCELADO
ACEITO ──(prestador inicia)──► EM_ANDAMENTO
EM_ANDAMENTO ──(prestador conclui + cliente confirma)──► CONCLUIDO  → split/repasse
EM_ANDAMENTO ──(qualquer parte abre disputa)──► EM_DISPUTA
EM_DISPUTA ──(mediação)──► CONCLUIDO | CANCELADO(reembolso)
ACEITO | EM_ANDAMENTO ──(cancelamento permitido)──► CANCELADO(reembolso)
```
Transação financeira (`transactions`): `PENDENTE → RETIDO → (LIBERADO | REEMBOLSADO)`; em falha → `PENDENTE` (retry idempotente).

## Convenções
- API REST `/api/v1`, JSON, erros padronizados via `@ControllerAdvice` (400/422/404…).
- Multimídia em object storage (S3/GCS); banco guarda só a URL.
- Diretiva Primária na Fase 4: não alterar sintaxe de código existente.

## Ponteiros
- Histórias completas: `./docs/spec.md`
- Blueprint técnico: `./ROADMAP.md`
- Identidade visual: `./design/tokens.css` + `./design/DESIGN.md`
