# Desempenho e fronteiras de domínio

## Executar a validação isolada

```sh
mvn -f backend/pom.xml test -Dtest=PerformanceBaselineTest --no-transfer-progress
```

Requer Java 21 e Docker. A suíte cria e destrói seu próprio PostGIS via Testcontainers,
usa dados sintéticos e gera JWTs de teste em memória. O cliente HTTP aceita somente
loopback com a porta aleatória do contexto Spring: não existe parâmetro de URL para
apontar esse benchmark à demo ou à produção. Não usa dinheiro nem provedores externos.
Storage, SMTP, FCM e gateway financeiro externos permanecem desligados/simulados.

No Docker Desktop Linux, caso o Ryuk tente montar o socket do host em vez do socket
da VM, usar `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` somente
nesse comando. Manter o Ryuk habilitado para limpar os recursos do teste.

A execução é opt-in, fora de `mvn test` normal. O workflow **Desempenho isolado**
executa a mesma suíte no PR e permite disparo manual após sua integração.
O artefato `performance-baseline` contém métricas e relatórios de teste.

## Cargas e critérios

- Geobusca: 10 mil perfis distribuídos em uma grade geográfica, 200 requisições de
  aquecimento separadas da medição, 20 clientes e mil buscas HTTP autenticadas;
  metade filtra categoria. Exige 20 resultados, zero erros e p95 inferior a 300 ms.
  Falhas/timeouts contam como erro e também entram na latência. Plano PostgreSQL
  `EXPLAIN ANALYZE/BUFFERS` acompanha o relatório.
- Chat: 32 conexões SSE de usuários distintos; 160 mensagens enviadas concorrentemente.
  O teste espera `ready`, mede a entrega SSE e verifica IDs únicos e isolamento por
  pedido. Depois desconecta 16 clientes, envia durante a ausência, reabre os canais,
  recupera o histórico por GET e exige novas entregas ao vivo. GET de recuperação
  não conta como entrega SSE. Todas as conexões são encerradas ao fim.
- Outbox: 400 eventos financeiros de agregados distintos, oito workers e gateway
  simulado com espera de 10 ms. Exige 400 chamadas únicas, 400 conclusões, zero
  claims remanescentes e gateway fora da transação de banco. O benchmark mede
  espera desde o enfileiramento até o término da chamada simulada; não é uma
  estimativa da latência de um provedor real. Recuperação financeira ambígua e SOS
  continuam cobertos por `E2EOutboxConcurrencyTest` e pelo contrato de
  [OUTBOX_FINANCEIRO.md](OUTBOX_FINANCEIRO.md).

## Como interpretar o relatório

`backend/target/performance/metrics.json` registra ambiente, configuração sintética,
contagem de amostras, erros, duração, vazão e percentis p50/p95/p99. O diretório pode
ser alterado com `-Dperformance.report-dir=/caminho/isolado`.

É um ensaio fechado, com aplicação, cliente e banco no mesmo computador/VM Docker.
A concorrência é fixa; a taxa oferecida diminui quando o serviço fica lento.
Não reproduz Internet móvel, TLS público, gateway real, autoscaling ou uma carga de
chegada constante. Os resultados qualificam apenas o cenário medido e não comprovam
capacidade da VPS nem cumprimento de SLA em produção. A meta de geobusca é aplicada
como limite do cenário; chat/fila registram uma linha de base sem inventar SLA novo.

## Fronteiras ORM alteradas

| Entidade | Referência anterior | Referência atual |
|---|---|---|
| Proposal | ServiceRequest | serviceRequestId UUID |
| Message | ServiceRequest | serviceRequestId UUID |
| ServiceRequest | User cliente | clienteId UUID |
| ProviderProfile | User | userId UUID |

As colunas, chaves estrangeiras e índices existentes continuam sob controle do
Flyway. Não há migração de dados nem mudança do JSON das APIs. As entidades não
carregam proxies de outro domínio nessas quatro fronteiras. As consultas que
precisam combinar dados usam joins explícitos ou UUIDs em lote.

`ProviderSummary`, `ProposalTiming` e `UserName` levam somente os campos necessários
às consultas. O painel recebe nome/status/CPF conciliado em uma consulta; o perfil
público busca nomes em lote; o histórico do prestador combina propostas, pedidos e
nomes em três consultas, sem uma nova consulta por linha. Leituras de CPF mantêm
os filtros por versão e por exclusão. Ownership, autorização de chat e ordem das
travas de pagamento/exclusão permanecem cobertos pelos E2E existentes.

`DomainReferencesTest` impede a reintrodução dos quatro vínculos ORM. Os testes
PostGIS adicionais verificam os valores das projeções, tempo de resposta,
pontualidade, ordenação/visibilidade do histórico e contagem de consultas.

O monólito continua compartilhando banco, serviços e alguns repositórios entre
módulos. Esta etapa reduz acoplamento ORM; não transforma os módulos em serviços
independentes nem adiciona mensageria distribuída ao SSE.
