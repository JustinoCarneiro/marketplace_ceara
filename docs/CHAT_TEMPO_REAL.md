# M15 / US39 — Chat em tempo real

O chat usa SSE para receber mensagens e mantém o POST REST existente para enviar.
O cliente deixa de consultar mensagens a cada quatro segundos. Não há novo broker,
serviço externo, migration ou credencial para esta funcionalidade.

## Contrato

- `POST /api/v1/service-requests/{id}/messages`: contrato anterior, com mascaramento de
  contatos antes de persistir. A resposta continua sendo `MessageDto`.
- `GET /api/v1/service-requests/{id}/messages`: histórico persistido, usado uma vez a cada
  abertura/reconexão para recuperar mensagens que chegaram durante a desconexão.
- `GET /api/v1/service-requests/{id}/messages/stream`: `text/event-stream`, com
  `Authorization: Bearer <access token>` no cabeçalho. Nunca usar JWT na URL.
- Eventos: `ready` (canal aberto), `message` (MessageDto, id da mensagem como SSE id),
  `heartbeat` (15 segundos), `reconnect` (rotação da conexão), `session-expired` e
  `access-revoked`. Eventos de controle têm corpo `{}`.
- Resposta sem cache (`Cache-Control: no-store`) e sem buffering de proxy
  (`X-Accel-Buffering: no`). O CORS aceita os cabeçalhos usados pelo cliente SSE.
- Erros de negócio anteriores à abertura mantêm o envelope JSON/HTTP 422. Sem JWT válido:
  HTTP 401. Papel sem permissão: HTTP 403. Limite de conexões: HTTP 429.

## Entrega e segurança

A abertura exige conta ativa e participação no pedido, a partir de ACEITO, incluindo
EM_ANDAMENTO, CONCLUIDO e EM_DISPUTA, como no envio existente. Conta do cliente excluída ou
suspensa fecha a conversa; CANCELADO/PENDENTE/PROPOSTO não oferecem canal ao vivo.
O fluxo não concede acesso administrativo adicional.

O envio publica apenas ids em um evento de domínio; somente AFTER_COMMIT agenda a leitura
assíncrona da mensagem persistida. Rollback não distribui mensagem. O conteúdo original com
telefone/e-mail não é incluído no evento: a leitura usa o texto mascarado que está no banco.
Cada conexão tem um escritor, que verifica novamente conta/participação antes de cada entrega
e nos heartbeats. Token expirado fecha a conexão sem entregar novas mensagens.

Há limite de 3 conexões por usuário e 256 na instância. Cada fila aceita até 64 mensagens;
consumidor lento é desconectado e recupera o histórico ao reconectar. Escritores usam o
`applicationTaskExecutor` configurado com virtual threads. Nenhuma conexão de banco fica
presa durante a espera por eventos.

A conexão gira a cada dois minutos (ou antes, no vencimento do JWT), limitando também o
buffer acumulado pelo XMLHttpRequest. O cliente usa `react-native-sse` 1.2.1, biblioteca
JavaScript sem dependências novas transitivas ou módulos nativos. A reconexão é controlada
pelo app, com espera progressiva de 1 a 30 segundos, detecção de silêncio em 45 segundos e
recuperação do histórico após o evento ready. Erros de sessão/permissão interrompem tentativas.
Não há renovação automática de sessão adicionada ao fluxo de autenticação existente.

O mobile fecha a conexão ao sair da tela ou ir para segundo plano e reconecta ao retornar.
Respostas de conexões antigas são descartadas; histórico, POST e SSE são combinados por id,
sem duplicar mensagens. A tela informa conexão, reconexão e indisponibilidade.

## Limites e verificação

O canal é local a uma instância do backend, correspondente à demo atual. Antes de escalar
para várias réplicas, será necessário distribuir os eventos entre instâncias (por exemplo,
broker); M18 trata separadamente da concorrência no outbox financeiro.

Uma reconexão recupera dados do banco; a memória do stream não é a fonte da verdade e não
há promessa de entrega exatamente uma vez. Proxies precisam permitir respostas HTTP
progressivas e respeitar o cabeçalho que desativa buffering.

Testes cobrem commit/rollback, autorização, expiração, encerramento assíncrono, CORS,
isolamento por pedido, reconexão, pausa e duplicatas. O E2E PostGIS usa HTTP SSE real;
o Playwright troca mensagens entre participantes sem recarregar nem consultar o histórico
para receber a resposta. Docker local indisponível: esses dois E2E são validados no CI.

Referências de implementação:
[Spring MVC — async/SSE](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html),
[react-native-sse](https://github.com/binaryminds/react-native-sse).
