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
- [[dependabot-alertas-e-politica-de-dependencias]] — Dependabot de segurança ligado em 2026-10-04: 29 alertas (21 altos), todos npm de admin/mobile, quase todos ferramenta de build. PR só de lockfile = diff + CI verde + squash; `package.json`/major = revisão manual; Maestro no master depois da leva. Três alertas aceitos sem correção possível (`braces`, `node-forge`, `uuid`, todos só no build do Expo); `admin/` ficou sem nenhuma vulnerabilidade — reavaliar a cada SDK.
- [[exclusao-de-conta-por-anonimizacao]] — exclusão de conta (US36, exigência das lojas e da LGPD): a linha de `users` não pode ser apagada (FKs sem cascata, aceite de termos imutável, transações a manter), então **anonimiza no lugar** — dados pessoais, textos livres, mídia, sessões e perfil saem; histórico financeiro e notas ficam. Recusa com pedido em curso, reembolso a caminho (cliente) ou repasse a receber (prestador, no Modelo A o admin precisa da chave Pix). `JwtAuthFilter` passou a conferir a conta a cada requisição (corte imediato do token, vale p/ suspensão também). **Decidido (2026-10-04)**; lacunas anteriores achadas no caminho: pedido `PROPOSTO` sem proposta ativa fica preso, e o cadastro de prestador nem grava nem consulta o hash do CPF.
- [[pedido-sem-prestador-volta-a-fila-cancela-e-expira]] — pedido `PROPOSTO` sem proposta ativa ficava preso e invisível (a fila só lista `PENDENTE`, o cliente não cancelava `PROPOSTO`, nada expirava). **Decidido (2026-10-04):** a última proposta ativa que sai (recusa ou prestador excluído) devolve o pedido a `PENDENTE`; o cliente cancela `PENDENTE`/`PROPOSTO` sem reembolso; 15 dias sem andamento (estado ou proposta nova) expiram. Muda a máquina de estados do `CLAUDE.md`.
- [[limite-de-tentativas-de-senha]] — nada limitava palpites de senha (login e, desde a US36, a exclusão de conta). **Decidido (2026-10-04):** 5 erros seguidos bloqueiam a conta por 15 min (429 + `Retry-After`, nem a senha certa entra), contador por conta no banco com trava de linha e `noRollbackFor` só nas duas exceções do limite; a recuperação por e-mail encerra o bloqueio. Trade-off aceito: quem sabe o e-mail pode bloquear a conta por 15 min.
- [[ios-preparacao-sem-conta-apple]] — iOS é requisito mas não havia conta Apple nem preparação: `app.json` com bundle id, flag de criptografia e textos de permissão em português só em primeiro plano (plugin de localização escrevia inglês + "Always" + movimento sem uso); perfil `development-simulator` no EAS; manifesto Android idêntico. Pendentes: conta Apple e teste em iPhone (a exclusão de conta, exigência das lojas, ficou pronta em 2026-10-04).
