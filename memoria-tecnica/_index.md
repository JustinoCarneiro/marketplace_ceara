---
tipo: indice
---

# Memória Técnica — Marketplace de Serviços Residenciais (Ceará)

Base de conhecimento viva do projeto: bugs cabeludos resolvidos (com causa raiz) e decisões técnicas
tomadas fora da spec original do [`CLAUDE.md`](../CLAUDE.md). Não documenta conceitos genéricos — só o
que é específico deste projeto e não seria óbvio olhando só o código.

Padrão da metodologia OndaDev (Memória Técnica Viva) — metodologia canônica no `onda-starter`; ponteiro local em [`docs/Metodologia_de_Desenvolvimento_-_Onda.md`](../docs/Metodologia_de_Desenvolvimento_-_Onda.md) pro
critério completo de quando vale (e quando não vale) criar uma nota aqui.

## Como usar
- **Antes de investigar um bug**, procurar em `bugs/` se algo parecido já foi resolvido.
- **Antes de tomar uma decisão de arquitetura**, procurar em `decisoes/` se já existe uma decisão
  relacionada (evita reabrir debate já resolvido ou contradizer uma decisão ativa).
- **Ao resolver um bug não-trivial** (que exigiu investigação real, causa raiz não óbvia a partir do
  código) ou **tomar uma decisão técnica fora da spec**, criar uma nota nova usando `templates/bug.md`
  ou `templates/decisao.md`, e linkar às notas relacionadas com a notação `[[nome-da-nota]]`.
- Não criar nota se o fato já tem um lar melhor (já é critério de aceite no `CLAUDE.md`, ou já tem um
  aviso dedicado em outro doc) — isso duplicaria a fonte de verdade em vez de complementá-la.

## Bugs
- [[e2e-fluxo-principal-quebrado]] — CI vermelho no master desde 13/08: DTO de proposta ganhou campo obrigatório, teste E2E não foi atualizado.
- [[jira-team-managed-endpoints-bloqueados]] — campo→layout é gap real de API; delete de issue era falta de papel atribuído, não permissão da plataforma.
- [[maestro-e2e-backend-nao-sobe]] — Maestro E2E falhava em toda run desde ~07/08: guard de canal de alerta bloqueava o boot do backend em CI.
- [[maestro-flows-nunca-validados-ao-vivo]] — com o boot corrigido, uma cadeia de ~9 causas raiz distintas (diálogo de sistema do teclado, corrida de foco, hideKeyboard incondicional causando BACK indevido, tap caindo em link aninhado, botões nunca validados, Maestro exigindo string inteira e não trecho, telas com scroll, passo da IA faltando) — 28 runs de validação até fechar `01`/`02`/`03`/`04`/`05` 100% verdes pela 1ª vez em mais de um mês.
- [[maestro-e2e-cleartext-bloqueado]] — com o formulário de cadastro finalmente submetendo, apareceu mais uma camada: `usesCleartextTraffic` do app.json é campo morto nesta versão do Expo, release não herdava permissão de HTTP puro pro backend local do emulador.

## Decisões
- [[mercadopago-escrow-modelo-de-repasse]] — MKT-49: split básico do Mercado Pago (auto na aprovação, Pix D0) não é o Escrow da spec (US06/US17). **Decidido (2026-09-10): Modelo A** — plataforma recebe o valor cheio e repassa ao prestador na conclusão (só Pix no piloto); migrar pro Modelo B (marketplace MP nativo) antes de escalar. Motor Saga/Outbox e máquinas de estado não mudam. **2026-09-13:** conta sem payout Pix programático ainda — `liberar` vira fila de repasse manual no admin até o MP habilitar.
- [[prestador-verificado-para-operar]] — a aprovação/suspensão do admin só gravava um status (filtrava a busca e nada mais): prestador em verificação, reprovado ou suspenso propunha e recebia. **Decidido (2026-10-04):** `ProviderVerificationGuard` — só `VERIFICADO` propõe, é contratado (o aceite do cliente confere de novo) e inicia serviço; a recusa vem antes de qualquer efeito. Fluxos de teste agora aprovam o prestador antes de propor (o Maestro, que não tem admin, grava no banco do CI).
