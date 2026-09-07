---
tipo: bug
data: 2026-09-07
severidade: Alta
status: Resolvido
resolvido_em: 2026-09-07
---

# `Mobile — Maestro E2E` falhava em toda run desde ~07/08 — backend nunca subia

## Sintoma
O job `Android API 29 — Maestro E2E` (`.github/workflows/mobile-e2e.yml`) falhava sempre no
passo **"Iniciar backend em background"**, em ~2m40s — antes até de instalar o Maestro CLI ou
subir o emulador. `gh run list --workflow=mobile-e2e.yml` mostra falha em toda run visível,
recuando até pelo menos 13/08/2026 (8 runs consecutivas conferidas). Passou despercebido por um
mês porque o workflow só dispara em push que toque `mobile/`, `backend/src/` ou o próprio arquivo
— baixa visibilidade, e a suspeita inicial documentada em memória (07/08) era outra: "testID não
visível ao Maestro no Android nativo", nunca confirmada como causa real deste job específico.

## Causa raiz
Não é testID, não é OOM de Gradle (esses já tinham sido corrigidos antes, ver comentários no
próprio workflow). O Spring Boot **crasha no boot**, antes de abrir a porta 8080:

```
BeanCreationException: Error creating bean with name 'alertChannelValidator':
  Invocation of init method failed
Caused by: java.lang.IllegalStateException:
  Nenhum canal de alerta externo configurado (e-mail e push desligados).
```

Esse guard existe desde a feature "SOS: canal de alerta" (07/08) — o app se recusa a subir sem
`MAIL_USERNAME`/`MAIL_PASSWORD` reais ou `ALLOW_MISSING_ALERT_CHANNEL=true` explícito, por
desenho (ver `notification.allow-missing-alert-channel` em `application.yml`). O passo "Iniciar
backend em background" do `mobile-e2e.yml` passa `JWT_SECRET`/`CPF_ENCRYPTION_KEY`/
`MARKETPLACE_WEBHOOK_SECRET` (confirmados existentes em `gh secret list`, desde 27/06 — não é
segredo faltando) mas nunca foi atualizado com a flag depois que o guard passou a existir. Sem a
porta 8080 respondendo, `timeout 90 bash -c 'until nc -z localhost 8080; do sleep 2; done'` estoura
e o job morre ali — o mesmo padrão de "guard novo bloqueia ambiente efêmero que ninguém lembrou de
atualizar" já visto no deploy de demo pública (`docker-compose.prod.yml`, 13/08).

## Solução
Adicionada `ALLOW_MISSING_ALERT_CHANNEL: "true"` ao `env:` do passo "Iniciar backend em
background", mesma flag e mesmo racional usados no ambiente de demo pública na VPS — CI não tem
(nem precisa ter) canal de alerta real. Nenhuma mudança de código de produção.

## Ligado a
- [[e2e-fluxo-principal-quebrado]] (outro CI vermelho no mesmo período, causa raiz diferente —
  DTO de proposta, não boot do backend)
- Ver também: feature "SOS: canal de alerta" (memória de sessão 2026-08-07) e
  `docker-compose.prod.yml` (deploy de demo, 2026-08-13) — mesmo guard, mesma flag.
