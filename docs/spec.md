# Especificação Viva — Histórias de Usuário e Critérios de Aceite

Projeto: **Marketplace de Serviços Residenciais (Ceará)** · Fase 1 (onda-spec-viva)
Fonte: TDD + Business Rulebook + decisões de escopo travadas (ver topo do `CLAUDE.md`).

Legenda de decisões de escopo MVP:
- Leilão → **versão simples** (propostas/orçamentos, sem lances em tempo real).
- Receita → **só comissão**. Assinaturas/boost/urgência fora do MVP.
- Botão SOS → **no MVP**. · IA de abertura de pedido → **no MVP, com fallback manual**.
- Avaliação → **bidirecional**.

---

## Épico 1 — Gestão de Identidade e Verificação

### US01 — Cadastro do Cliente
Como **Cliente**, quero me cadastrar com e-mail e senha, para acessar prestadores com segurança.
- **Dado que** informo e-mail válido e senha forte, **quando** confirmo o cadastro, **então** recebo JWT + refresh token e role `ROLE_CLIENT`.
- **Dado que** o e-mail já existe, **quando** tento cadastrar, **então** recebo 422 com mensagem padronizada.
- **Dado** qualquer cadastro, **então** a senha é persistida apenas como hash (nunca em texto puro).

### US02 — Perfil e verificação do Prestador
Como **Prestador**, quero criar perfil visual e enviar CPF para validação, para transmitir credibilidade.
- **Dado que** envio meu CPF, **quando** finalizo o cadastro, **então** o background check é disparado de forma **assíncrona** e meu status fica `EM_VERIFICACAO`.
- **Dado que** o background check retorna aprovado, **então** meu status vira `VERIFICADO` e passo a aparecer nas buscas.
- **Dado que** retorna reprovado/inconclusivo, **então** status `REPROVADO` e não apareço nas buscas.
- **Dado** o CPF armazenado, **então** ele é criptografado em repouso (LGPD).

### US12 — Sessão persistente (refresh token)
Como **Usuário**, quero permanecer logado com segurança, para não reautenticar a cada uso.
- **Dado que** meu JWT expira, **quando** envio um refresh token válido, **então** recebo um novo par de tokens.
- **Dado que** o refresh token é revogado/expirado, **então** recebo 401 e sou redirecionado ao login.

---

## Épico 2 — Descoberta e Geobusca

### US03 — Lista de profissionais por categoria e proximidade
Como **Cliente**, quero ver profissionais por categoria e proximidade, para achar ajuda perto de casa.
- **Dado que** informo lat/lng e categoria, **quando** busco, **então** recebo prestadores `VERIFICADO` ordenados por distância usando **PostGIS** (índice espacial).
- **Dado** a chamada `GET /api/v1/providers/nearby`, **então** o p95 responde em < 300ms.
- **Dado que** não há prestadores no raio, **então** recebo lista vazia com estado tratado (não erro).

### US13 — Filtros básicos
Como **Cliente**, quero filtrar por distância e nota mínima, para refinar a escolha.
- **Dado** um raio e uma nota mínima, **quando** aplico o filtro, **então** só vejo prestadores que atendem ambos.

---

## Épico 3 — Solicitação Multimídia + IA

### US04 — Abertura de pedido multimídia
Como **Cliente**, quero abrir um pedido com texto, áudio e/ou foto, para o prestador avaliar antes de aceitar.
- **Dado que** anexo mídia, **quando** envio, **então** o app solicita permissões nativas de Câmera/Microfone e o arquivo vai para o object storage; o banco guarda só a URL.
- **Dado** o pedido criado via `POST /api/v1/service-requests` (JSON — categoria/descrição/localização), **então** cada mídia é anexada em seguida por `POST /api/v1/service-requests/{id}/media` (`multipart/form-data`, campos `file` + `tipo` ∈ `FOTO`/`AUDIO`/`TEXTO`); são duas chamadas, não uma — falha ao anexar não desfaz o pedido já criado.
- **Dado** que anexo mídia a um pedido, **então** só cliente dono ou prestador com proposta aceita podem anexar (mesma regra de participação do detalhe do pedido); e o `tipo` declarado precisa bater com o content-type do arquivo (`FOTO`→`image/*`, `AUDIO`→`audio/*`).
- Reaproveita `RateScreen` (avaliação bidirecional, Épico 7): anexar foto à avaliação usa o mesmo endpoint, contra o mesmo `requestId` já `CONCLUIDO`.

### US14 — Assistente de IA para abrir o pedido
Como **Cliente**, quero que a IA leia minha foto/texto e sugira a descrição e uma faixa de orçamento, para abrir o pedido mais rápido.
- **Dado que** envio foto/texto, **quando** a IA processa, **então** recebo descrição sugerida + faixa de orçamento editáveis antes de confirmar.
- **Dado que** a IA falha ou está indisponível, **então** o fluxo continua manualmente sem bloquear a abertura do pedido (**fallback obrigatório**).
- **Dado** qualquer sugestão da IA, **então** ela é tratada como rascunho — o Cliente confirma o conteúdo final.

---

## Épico 4 — Propostas e Orçamentos (leilão simples)

### US15 — Prestador envia proposta
Como **Prestador**, quero enviar uma proposta de preço para um pedido, para concorrer ao serviço.
- **Dado** um pedido `PENDENTE` na minha categoria/raio, **quando** envio valor + prazo, **então** o pedido recebe minha proposta e passa a `PROPOSTO`.
- **Dado** que o pedido já foi aceito por outro, **quando** tento propor, **então** recebo 422 (pedido indisponível).

### US16 — Cliente compara e aceita proposta
Como **Cliente**, quero comparar propostas e aceitar uma, para contratar com preço justo.
- **Dado** múltiplas propostas, **quando** aceito uma, **então** as demais são encerradas e o fluxo segue para pagamento/Escrow.
> Nota: sem lances em tempo real no MVP (decisão de escopo). Evolução para leilão dinâmico → v2.

---

## Épico 5 — Pagamento e Escrow

### US05 — Pagamento via Pix ou Cartão
Como **Cliente**, quero pagar via Pix ou Cartão no app, para ter comodidade.
- **Dado** uma proposta aceita, **quando** escolho Pix/Cartão, **então** o gateway gera a cobrança e o pedido aguarda confirmação.
- **Dado** a chamada de pagamento, **então** ela exige **chave de idempotência** (reenvio não duplica cobrança).

### US06 — Retenção (Escrow)
Como **Cliente**, quero meu dinheiro retido na plataforma até o serviço terminar, para não sofrer golpe.
- **Dado** pagamento confirmado por **webhook**, **quando** o evento é processado, **então** a `transaction` vai para `RETIDO` e o pedido para `ACEITO`.
- **Dado** falha de rede com o gateway, **então** **não** há rollback de banco sobre cobrança externa: o estado segue por Saga/Outbox e é reconciliado por evento (sem perda nem cobrança fantasma).

### US07 — Visibilidade da retenção para o Prestador
Como **Prestador**, quero ver que o cliente pagou e o valor está retido, para iniciar sem risco de calote.
- **Dado** `transaction` `RETIDO`, **quando** abro o painel, **então** vejo o pedido marcado como "pago e retido".

### US17 — Liberação / split na conclusão
Como **Plataforma**, quero liberar o valor com split de comissão ao concluir, para remunerar prestador e plataforma.
- **Dado** o pedido em `CONCLUIDO`, **quando** o evento de conclusão é confirmado, **então** o split é acionado (comissão 10–25% para a plataforma, restante ao prestador) e a `transaction` vai para `LIBERADO`.

### US18 — Disputa e reembolso
Como **Cliente ou Prestador**, quero abrir disputa quando algo der errado, para acionar mediação.
- **Dado** pedido `EM_ANDAMENTO`, **quando** abro disputa, **então** o pedido vai para `EM_DISPUTA` e o valor permanece retido.
- **Dado** decisão da mediação, **então** a `transaction` vai para `LIBERADO` (a favor do prestador) ou `REEMBOLSADO` (a favor do cliente).

---

## Épico 6 — Execução do Serviço

### US19 — Ciclo de vida do chamado
Como **Prestador**, quero atualizar o andamento do serviço, para refletir o status real ao cliente.
- **Dado** pedido `ACEITO`, **quando** inicio, **então** vai para `EM_ANDAMENTO`.
- **Dado** pedido `EM_ANDAMENTO`, **quando** concluo e o cliente confirma, **então** vai para `CONCLUIDO`.
- **Dado** cancelamento permitido por regra, **quando** acionado, **então** vai para `CANCELADO` com reembolso quando houver valor retido.
- Transições inválidas (ex.: `PENDENTE → CONCLUIDO`) são rejeitadas com 422.

---

## Épico 7 — Avaliação Bidirecional e Reputação

### US08 — Cliente avalia Prestador
Como **Cliente**, quero dar 1–5 estrelas e comentar, para ajudar outros a escolher.
- **Dado** pedido `CONCLUIDO`, **quando** avalio, **então** a nota entra no cálculo da nota média do prestador **assim que for revelada** (ver US31).
- **Dado** pedido não concluído, **quando** tento avaliar, **então** recebo 422.
- **Dado** que não sou o cliente dono do pedido, **quando** tento avaliar, **então** recebo 422
  `FORBIDDEN` — avaliar exige ser de fato quem participou do serviço, não só um pedido `CONCLUIDO`
  qualquer (senão qualquer conta fabrica reputação avaliando pedido de terceiros).

### US20 — Prestador avalia Cliente
Como **Prestador**, quero avaliar o Cliente após o serviço, para sinalizar bons/maus contratantes.
- **Dado** pedido `CONCLUIDO`, **quando** avalio o cliente (1–5 + comentário), **então** a nota entra na reputação dele.
- Avaliações são liberadas para ambos somente após `CONCLUIDO` (sem retaliação durante o serviço).
- **Dado** que não sou o prestador com proposta `ACEITA` do pedido, **quando** tento avaliar,
  **então** recebo 422 `FORBIDDEN` (mesma regra de participação do US08).

### US32 — Canal de denúncia
Como **Cliente ou Prestador**, quero reportar um prestador ou uma avaliação que pareça
fraudulenta, para que a moderação analise.
- **Dado** que denuncio, **então** escolho um motivo (perfil falso, comportamento inadequado,
  conteúdo ofensivo, golpe/fraude ou outro) e posso adicionar detalhes opcionais.
- **Dado** que a denúncia é registrada, **então** ela entra na fila de moderação do admin
  (Épico 9) e dispara um alerta operacional — mesma central do SOS/disputa/verificação.
- **Dado** que o alvo (prestador ou avaliação) não existe, **então** recebo 422.
- A denúncia em si não pune ninguém automaticamente — só o admin resolve, pelos mecanismos de
  moderação já existentes (suspender usuário, reprovar prestador).

### US31 — Avaliação double-blind (reveal simultâneo)
Como **plataforma**, quero que as duas avaliações só apareçam ao mesmo tempo, para que ninguém
escreva a sua olhando a nota que recebeu (retaliação) nem combine notas altas mútuas (conluio).
- **Dado** que avaliei e a outra parte ainda não, **então** minha avaliação fica **oculta**: não
  aparece no perfil público nem entra na nota média — a média também vazaria a nota escondida.
- **Dado** que a outra parte avalia depois, **então** as **duas** são reveladas no mesmo instante
  e a reputação é recalculada.
- **Dado** que a outra parte nunca avalia, **quando** vence o prazo de reciprocidade
  (`marketplace.review.reveal-deadline-days`, padrão **14 dias**), **então** minha avaliação é
  publicada assim mesmo — quem avaliou não pode ser punido pelo silêncio do outro.
- **Dado** que avaliei, **então** vejo na confirmação se já está pública ou até quando fica oculta.

---

## Épico 8 — Segurança do Usuário (Botão SOS)

### US21 — Acionamento de emergência
Como **Usuário em atendimento**, quero um Botão SOS, para pedir ajuda em situação de risco.
- **Dado** um pedido `EM_ANDAMENTO`, **quando** aciono o SOS, **então** o evento é registrado com localização e um canal de emergência/contato é acionado.
- **Dado** o acionamento, **então** ele fica auditável (quem, quando, onde) para a mediação/suporte.

---

## Épico 9 — Administração e Mediação (painel web)

> Superfície **web** (React), separada do app mobile. Todo acesso exige `ROLE_ADMIN`. É onde a plataforma é gerenciada e onde a "mediação" prometida no Épico 5 (US18) de fato acontece.

### US22 — Acesso administrativo
Como **Admin**, quero entrar num painel restrito, para gerenciar a plataforma com segurança.
- **Dado que** informo credenciais de admin, **quando** autentico, **então** recebo sessão com `ROLE_ADMIN` e acesso ao painel web.
- **Dado** um usuário sem `ROLE_ADMIN`, **quando** tenta acessar qualquer rota administrativa, **então** recebe 403.
- **Dado** qualquer ação administrativa, **então** ela é registrada em log de auditoria (quem, o quê, quando).

### US23 — Dashboard de métricas
Como **Admin**, quero ver as métricas-chave do negócio, para acompanhar a saúde da plataforma.
- **Dado** o painel inicial, **então** vejo: volume transacionado (GMV), receita de comissão, ticket médio, nº de pedidos por status, taxa de conclusão, disputas abertas e tempo médio de resolução, prestadores verificados/ativos, clientes ativos e SOS acionados no período.
- **Dado** um filtro de período/bairro, **quando** aplico, **então** as métricas recalculam.
- **Dado** uma métrica financeira, **então** ela bate com o estado das `transactions` (consistência com o Escrow).

### US24 — Mediação de disputas
Como **Admin (mediador)**, quero analisar e resolver disputas, para destravar o valor retido de forma justa.
- **Dado** a fila de disputas, **quando** abro uma `EM_DISPUTA`, **então** vejo o histórico do pedido, as partes, as mídias e o valor retido.
- **Dado** minha decisão, **quando** resolvo a favor do prestador, **então** a `transaction` vai para `LIBERADO` (split) e o pedido para `CONCLUIDO`.
- **Dado** minha decisão, **quando** resolvo a favor do cliente, **então** a `transaction` vai para `REEMBOLSADO` e o pedido para `CANCELADO`.
- **Dado** que a resolução move dinheiro, **então** ela passa pelo mesmo motor Saga/Outbox/idempotência (nunca `@Transactional` sobre o gateway).

### US25 — Moderação de prestadores
Como **Admin**, quero revisar prestadores, para garantir a qualidade e a segurança da base.
- **Dado** um background check `INCONCLUSIVO`, **quando** reviso manualmente, **então** posso definir `VERIFICADO` ou `REPROVADO` com justificativa.
- **Dado** um prestador problemático, **quando** o suspendo, **então** ele deixa de aparecer nas buscas e não recebe novos pedidos.

### US26 — Gestão de usuários
Como **Admin**, quero buscar e gerenciar usuários, para dar suporte e conter abusos.
- **Dado** uma busca por e-mail/nome, **quando** localizo um usuário, **então** vejo seu perfil, histórico e status.
- **Dado** um usuário em abuso, **quando** o suspendo/reativo, **então** o acesso dele é bloqueado/liberado e a ação fica auditável.

### US27 — Reconciliação financeira (Escrow)
Como **Admin**, quero acompanhar o estado das transações e dos eventos, para garantir que nenhum valor fique preso ou inconsistente.
- **Dado** a visão de transações, **então** filtro por `PENDENTE/RETIDO/LIBERADO/REEMBOLSADO` e vejo divergências.
- **Dado** um `outbox_event` em `FALHA`, **quando** aciono o reprocessamento, **então** ele é reenfileirado de forma idempotente (sem cobrança/repasse duplicado).

### US28 — Gestão de categorias de serviço
Como **Admin**, quero manter o catálogo de categorias, para refletir os serviços ofertados por bairro.
- **Dado** o catálogo, **quando** crio/edito/desativo uma categoria, **então** a mudança reflete nas buscas e na abertura de pedidos.

### US29 — Exportação de relatórios
Como **Admin**, quero exportar relatórios, para análise externa e prestação de contas.
- **Dado** o dashboard ou uma listagem (transações, disputas, pedidos), **quando** aciono exportar, **então** recebo o arquivo em **CSV** (e **PDF** para o resumo de métricas) respeitando os filtros aplicados.
- **Dado** dados sensíveis (LGPD), **então** o relatório não expõe CPF nem dados além do necessário.

### US30 — Alertas operacionais ao Admin
Como **Admin**, quero ser alertado de eventos críticos, para agir rápido em segurança e disputas.
- **Dado** um **SOS acionado** (US21), **quando** o evento é registrado, **então** o admin recebe alerta **imediato** (push/e-mail) — caminho de segurança, prioridade máxima.
- **Dado** que nenhum canal externo está configurado, **quando** a aplicação sobe, **então** ela **se recusa a iniciar** — a não ser que a ausência seja declarada de propósito (`notification.allow-missing-alert-channel=true`, para dev/demo/CI). Sem isso, faltar credencial de SMTP deixava a plataforma rodando sem canal de emergência, em silêncio.
- **Dado** que a entrega do alerta de SOS falha (SMTP fora do ar), **então** o evento do Outbox fica em `FALHA` — aparece na reconciliação (US27), é reprocessável, e o alerta continua visível no painel. A entrega do SOS **não** é best-effort: passa pelo Outbox justamente para não depender de uma tentativa única.
- **Dado** uma **disputa aberta** (US18), **então** o admin é notificado para iniciar a mediação.
- **Dado** um **background check `INCONCLUSIVO`** (US02), **então** o admin é notificado para moderar manualmente.
- **Dado** os alertas, **então** há uma **central de notificações** no painel (não lidas/lidas), e o alerta de SOS nunca depende só do painel (push/e-mail garantido).

---

## Histórias Técnicas (Requisitos Não Funcionais)

- **TS01 (Concorrência):** Virtual Threads (Java 21) para suportar picos sem custo extra de servidor.
- **TS02 (Integridade financeira):** Saga + Outbox + idempotência no Escrow; **proibido** `@Transactional` envolvendo chamada ao gateway.
- **TS03 (Geo):** PostGIS com índice GiST; `nearby` p95 < 300ms.
- **TS04 (LGPD):** CPF/dados sensíveis criptografados em repouso; DTOs (Records) ocultam entidades.
- **TS05 (Auth):** JWT curto + refresh token; controle de acesso por role nos endpoints.
- **TS06 (Erros):** `@ControllerAdvice` padronizando 400/404/422 em JSON.
- **TS07 (Mídia):** upload para object storage; banco persiste apenas URL.
- **TS08 (Observabilidade):** logs estruturados + métricas dos fluxos de pagamento e disputa (entra no detalhe na Fase 3).
- **TS09 (Admin/Auditoria):** painel web restrito a `ROLE_ADMIN` (403 para os demais); toda ação administrativa registrada em log de auditoria imutável (quem/o quê/quando). Métricas derivadas por consulta/agregação, sem duplicar a fonte da verdade financeira.

---

## Dicionário de dados (reconciliado com as migrations Flyway V1–V7 em 2026-06-28)
- `users` (id, nome, email, cpf_cifrado, senha_hash, role)
- `refresh_tokens` (id, user_id, token_hash, expires_at, revogado, created_at) — sessão persistente (US12)
- `providers_profile` (id, user_id, categoria, status_verificacao, saldo_retido, nota_media)
- `background_checks` (id, provider_id, status, resultado, requested_at, completed_at) — verificação assíncrona (US02)
- `service_categories` (id, nome, slug, ativa) — catálogo do admin (US28)
- `service_requests` (id, cliente_id, prestador_id, descricao, status)
- `service_media` (id, service_request_id, tipo, url, created_at) — mídia em object storage; banco só a URL (TS07)
- `proposals` (id, service_id, prestador_id, valor, prazo, status)
- `transactions` (id, service_id, valor_total, valor_comissao, status_pagamento, gateway_transaction_id, idempotency_key)
- `outbox_events` (id, agregado, tipo_evento, payload, status, criado_em) — motor do Escrow
- `reviews` (id, service_id, avaliador_id, avaliado_id, nota, comentario, url_imagem)
- `sos_alerts` (id, user_id, service_request_id, latitude, longitude, status, criado_em, resolvido_em) — Botão SOS (US21)
- `dispute_resolutions` (id, service_request_id, admin_id, decisao, justificativa, criado_em) — mediação (US24)
- `admin_notifications` (id, tipo, ref_id, lida, criado_em) — central de alertas do admin (US30)

- `admin_audit_log` (id, admin_id, acao, recurso, recurso_id, detalhe, criado_em) — trilha imutável de ações administrativas (US22/TS09). Migration `V8__admin_audit_log.sql`; registrada em `AuditService` e consultada em `GET /api/v1/admin/audit`.
- `users.cpf_hash` — hash determinístico HMAC-SHA256 (antifraude Camada 2, unicidade de CPF de cliente sem guardar em claro), migration `V9__user_cpf_hash.sql`; gravado em `POST /api/v1/auth/verify-identity`.
- `service_requests.motivo_disputa` / `detalhes_disputa` — motivo informado ao abrir a disputa (US18), migration `V10__dispute_reason.sql`.
- `reviews.revelada` / `revelada_em` — double-blind (US31), migration `V11__review_double_blind.sql`.
- `denuncias` (id, tipo, alvo_id, denunciante_id, motivo, detalhes, status, resolvido_por_id, resolvido_em, criado_em) — canal de denúncia de prestador/avaliação fraudulenta (US32), migration `V12__denuncia.sql`.
- `terms_acceptance` (id, user_id, doc_version, accepted_at, ip_address) — prova de aceite dos Termos/Privacidade no cadastro (`docs/PENDENCIAS_JURIDICAS.md` item 3), migration `V13__terms_acceptance.sql`; gravada em `POST /api/v1/auth/register/{client|provider}`, que rejeita (422) sem `aceitouTermos:true`.

---

## Itens fora do MVP (registrados para v2)
Leilão dinâmico em tempo real · Assinaturas SaaS (Profissional/Elite) · Boost de visibilidade · Taxa de urgência/noturna · Filtros avançados · Modo Plantão 24h · Selo Verificado avançado · IA de orçamento automático end-to-end · Monitoramento de trajeto por GPS · Crédito/seguros · Expansão multi-região.
