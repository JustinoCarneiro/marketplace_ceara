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
- **Dado** o endpoint `POST /api/v1/services/requests`, **então** ele aceita `multipart/form-data` e cria o pedido em `PENDENTE`.

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
- **Dado** pedido `CONCLUIDO`, **quando** avalio, **então** a nota entra no cálculo da nota média do prestador.
- **Dado** pedido não concluído, **quando** tento avaliar, **então** recebo 422.

### US20 — Prestador avalia Cliente
Como **Prestador**, quero avaliar o Cliente após o serviço, para sinalizar bons/maus contratantes.
- **Dado** pedido `CONCLUIDO`, **quando** avalio o cliente (1–5 + comentário), **então** a nota entra na reputação dele.
- Avaliações são liberadas para ambos somente após `CONCLUIDO` (sem retaliação durante o serviço).

---

## Épico 8 — Segurança do Usuário (Botão SOS)

### US21 — Acionamento de emergência
Como **Usuário em atendimento**, quero um Botão SOS, para pedir ajuda em situação de risco.
- **Dado** um pedido `EM_ANDAMENTO`, **quando** aciono o SOS, **então** o evento é registrado com localização e um canal de emergência/contato é acionado.
- **Dado** o acionamento, **então** ele fica auditável (quem, quando, onde) para a mediação/suporte.

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

---

## Dicionário de dados (base — refinar na Fase 3)
- `users` (id, nome, email, cpf_cifrado, senha_hash, role)
- `providers_profile` (id, user_id, categoria, status_verificacao, saldo_retido, nota_media)
- `service_requests` (id, cliente_id, prestador_id, descricao, url_midia, status)
- `proposals` (id, service_id, prestador_id, valor, prazo, status)
- `transactions` (id, service_id, valor_total, valor_comissao, status_pagamento, gateway_transaction_id, idempotency_key)
- `reviews` (id, service_id, avaliador_id, avaliado_id, nota, comentario, url_imagem)
- `outbox_events` (id, agregado, tipo_evento, payload, status, criado_em) — motor do Escrow
- `sos_events` (id, service_id, user_id, lat, lng, criado_em)

---

## Itens fora do MVP (registrados para v2)
Leilão dinâmico em tempo real · Assinaturas SaaS (Profissional/Elite) · Boost de visibilidade · Taxa de urgência/noturna · Filtros avançados · Modo Plantão 24h · Selo Verificado avançado · IA de orçamento automático end-to-end · Monitoramento de trajeto por GPS · Crédito/seguros · Expansão multi-região.
