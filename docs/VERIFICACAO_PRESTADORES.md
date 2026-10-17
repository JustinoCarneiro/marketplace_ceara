# Verificação de prestadores — US02 / M02

O cadastro grava uma tarefa `PENDENTE` em `background_checks` na mesma transação do
perfil. Não depende de uma chamada externa nem de uma tarefa em memória. A V31
adiciona contador de tentativas, prazo de disponibilidade e token do worker.
Perfis anteriores à entrega continuam disponíveis na fila de moderação existente;
esta migration não dispara consultas retroativas nem altera verificações passadas.

## Processamento e contingência

O worker consulta até 25 tarefas a cada 60 segundos (configurável por
`marketplace.background-check.delay-ms`). Reserva cada tarefa sob trava de linha,
com lease de 120 segundos e token exclusivo, e confirma a transação antes da
consulta. Outros workers não processam o mesmo lease. Uma queda deixa a tarefa
recuperável ao expirar o prazo; respostas com tokens antigos são descartadas.
O UUID da tarefa é a chave de idempotência que o futuro fornecedor deve aceitar.

Erros e respostas nulas recebem até três tentativas, com espera de 60 e 120
segundos. Ao esgotar o limite, a consulta vira `INCONCLUSIVO`, com código
`FALHA_CONSULTA`, e gera alerta `VERIFICACAO` no painel. Quedas após a reserva
consomem tentativas para evitar repetição ilimitada. Falha ao persistir o resultado
ou alerta causa rollback conjunto e recuperação após o lease.

Sem fornecedor contratado, `ManualBackgroundCheckGateway` devolve `INCONCLUSIVO`
sem decifrar ou transmitir CPF. O resultado é `REVISAO_MANUAL`; o perfil permanece
`EM_VERIFICACAO`, fora das buscas e bloqueado para propostas/contratação até a
moderação existente decidir. O alerta é persistido no painel; envio por e-mail e
push não faz parte desta fila. Não há aprovação automática simulada em produção.

Um futuro resultado documentado `APROVADO` permite `VERIFICADO`, e `REPROVADO`
permite `REPROVADO`. Antes de gravar, a finalização trava o usuário (como moderação
e exclusão) e relê perfil/conta. Conta inativa, excluída ou status já moderado
cancela a tarefa sem sobrescrever essa decisão. CPF não conciliado sempre exige
revisão manual. Nenhum laudo ou resposta bruta é armazenado ou registrado em log.

## Integração externa ainda pendente

Não existe conector real habilitado. Para implementá-lo são necessários fornecedor,
contrato da API, estados/resultados documentados, política de uso e credenciais.
A porta `BackgroundCheckGateway` fornece UUID da tarefa e acesso ao CPF sob demanda
por `Supplier<String>`. O adaptador deve limitar timeout a menos de 120 segundos,
tratar idempotência, autenticar respostas e mapear apenas resultados documentados.
A consulta roda com transação suspensa, inclusive se o worker for chamado dentro
de outra transação. Segredos ficam fora do Git; respostas contendo dados pessoais
não podem aparecer em exceções de aplicação, logs, DTOs ou relatórios públicos.

Não foram adicionados endpoint de callback nem aprovação por parâmetros de
configuração. A contratação e análise do fornecedor permanecem pendentes.

## Validação

Testes unitários cobrem aprovação, reprovação, fallback manual sem decifrar CPF,
limite de tentativas, resposta nula, leases, exclusão, moderação e CPF não
conciliado. Cenários PostGIS cobrem persistência, rollback do cadastro e workers
concorrentes recuperando um lease sem duplicar o alerta.
