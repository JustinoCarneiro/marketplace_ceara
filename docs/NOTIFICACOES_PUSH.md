# Notificações push — Android / Firebase Cloud Messaging

A integração usa FCM HTTP v1 no backend e `expo-notifications` no Android.
PostgreSQL, autenticação e regras de negócio continuam nos serviços existentes.
O push não substitui o chat SSE e não envia e-mails.

## Comportamento

O usuário abre **Perfil → Notificações → Ativar**. A permissão do Android só é
solicitada nessa ação, após confirmar que o backend tem push habilitado. Recusar
não impede usar o aplicativo. A preferência fica no SecureStore, por conta e
celular. Há também a ação Desativar.

São enfileirados avisos de nova proposta, proposta aceita, nova mensagem,
serviço iniciado, serviço concluído e confirmação de pagamento. O remetente da
mensagem não recebe seu próprio aviso. Os destinatários são o cliente e o
prestador da proposta aceita, conforme o evento; o cliente recebe nova proposta.

O payload exibe apenas “Onda” e “Você tem uma atualização. Abra o aplicativo para
consultar.” Envia tipo e UUID de referência/entrega, sem nome, endereço, conteúdo
do chat, CPF ou valores financeiros. Tocar no aviso abre o aplicativo; esta
entrega não navega automaticamente a uma tela de dados. A API mantém suas
verificações de autorização.

## Cadastro e privacidade

`GET /api/v1/push/status` informa `enabled` para usuários autenticados.
`POST /api/v1/push/registrations` recebe `{ token }` autenticado e devolve uma
revocationKey aleatória de 256 bits, válida para ativação por cinco minutos.
`PUT /api/v1/push/devices` cadastra `{ token, revocationKey }` e retorna 204.
O dono vem da autenticação, nunca do corpo. Aceita até cinco dispositivos por
conta. `DELETE` no mesmo caminho só apaga cadastros do usuário autenticado.
Tokens são cifrados com AES-GCM (a chave de criptografia já configurada no
backend), com SHA-256 para unicidade; nunca aparecem em DTOs, URLs ou logs.

A revocationKey é gerada pelo servidor para cada tentativa autenticada, vinculada
à conta e ao hash do token, e guardada no SecureStore do celular. O servidor guarda
apenas seu hash. Reservas são limitadas a 120 por conta/hora e removidas após dois
dias. A revogação pública não cria reservas nem registros arbitrários. `POST /api/v1/push/revoke`
recebe token + revocationKey e permite somente revogar o cadastro correspondente,
com 204 tanto para registro existente quanto ausente. Essa capacidade não dá
acesso a dados, não registra dispositivos e permite concluir a revogação após
logout ou expiração da sessão. Uma capacidade antiga não revoga um cadastro
transferido para outra conta. A reserva é travada na ativação/revogação: revogar
marca a tentativa como revogada, inclusive se o PUT ainda não chegou ou aguarda
uma trava. Um registro atrasado não pode recriar um dispositivo revogado.

Registro, rotação, ativação, desativação e troca de sessão são coordenados no
celular. Antes de enviar um cadastro, ele salva a capacidade localmente, cobrindo
perda da resposta. Revogações são persistidas antes da tentativa de rede e
retomadas na inicialização, reconexão e retorno do aplicativo, mesmo sem login.
Sem rede, o servidor não pode receber uma revogação imediatamente: um aviso
pendente pode chegar até reconectar ou expirar o cadastro. O texto permanece
genérico. O cadastro expira após **24 horas sem renovação**; o app renova quando
aberto/retomado. Esse limite também limita avisos após longo período sem abrir
o app. Capacidades pendentes são descartadas localmente após 48 horas.

Trocar de dono muda a geração do cadastro e invalida entregas da conta anterior.
Excluir a conta remove dispositivos e entregas pendentes na mesma transação.
Contas inativas/excluídas e gerações antigas não são elegíveis no worker. Uma
mensagem já aceita pelo FCM pode estar em trânsito e não pode ser recolhida.

## Durabilidade e falhas

A V32 cria `push_devices`, `push_deliveries` e `push_registration_fences`. O listener executa antes do commit,
confirma os dados JPA no banco e insere tarefas na transação de origem. Rollback
não deixa notificações. A seleção protege os dispositivos contra exclusão
concorrente; a unicidade por dispositivo/geração/tipo/referência evita tarefas
repetidas. Sem push habilitado não são criadas tarefas.

O worker reserva até 25 tarefas por ciclo, usando `SKIP LOCKED`, lease de cinco
minutos e token de reserva. A chamada ao FCM acontece sem transação de banco.
Há até cinco tentativas, com espera crescente de pelo menos um minuto e respeito
a `Retry-After`. Após interrupção, o lease permite retomar; respostas de leases
antigos não finalizam a reserva atual. Entregas têm validade de 30 minutos,
e histórico técnico de sete dias. `UNREGISTERED` remove o dispositivo; erro de
payload/autorização falha a tarefa, sem apagar um dispositivo potencialmente
válido. O conteúdo bruto dos erros nunca é registrado.

A entrega é **pelo menos uma vez**: se o processo parar após o FCM aceitar o envio
e antes de gravar o resultado, um aviso pode se repetir. `deliveryId` permanece
estável nas tentativas. Aceitação pelo FCM não comprova leitura ou entrega ao
aparelho.

Alertas administrativos existentes usam o mesmo transporte quando houver
dispositivo de uma conta ADMIN. O cadastro desse dispositivo é feito pela API
com sessão ADMIN; não há interface de administrador no app cliente/prestador.
O canal só conta como disponível no validador de alertas quando há ao menos um
dispositivo administrativo ativo. A fila durável de SOS e sua reconciliação
permanecem existentes. Falhas push são traduzidas para a exceção contratada,
inclusive 429/5xx, evitando erro HTTP depois do commit de uma operação.

## Ativação operacional ainda pendente

Nada precisa ser habilitado para desenvolvimento, CI ou demo: o padrão é
`FCM_ENABLED=false` e `EXPO_PUBLIC_PUSH_ENABLED` ausente. Nenhuma credencial é
consultada e não há tráfego ao Google nesse modo.

Para ativar, o responsável precisa:

1. Criar projeto Firebase e cadastrar o Android `com.onda.marketplace`.
2. Habilitar Cloud Messaging HTTP v1 e preparar identidade do servidor com
   permissão mínima de envio. Montar credenciais fora do Git e da imagem, usando
   `GOOGLE_APPLICATION_CREDENTIALS`; preferir identidade de workload quando disponível.
   No Coolify/Compose, montar o arquivo protegido no backend por volume/secret;
   definir o caminho no ambiente não monta o arquivo. O compose repassa essas
   variáveis com FCM desligado por padrão.
3. Configurar `FCM_ENABLED=true` e `FCM_PROJECT_ID` no servidor. Configuração
   incompleta deve falhar na inicialização; não há fallback silencioso quando ligado.
4. Fornecer o arquivo `google-services.json` ao build Android pelo caminho em
   `GOOGLE_SERVICES_JSON`; manter o arquivo fora do Git. Definir
   `EXPO_PUBLIC_PUSH_ENABLED=true` no build.
5. Gerar **novo build nativo 1.2.0**: novas dependências nativas exigem rebuild;
   atualização OTA do aplicativo 1.1.0 não basta. A política runtimeVersion por
   appVersion mantém esses runtimes separados.
6. Validar em dispositivo Android com Google Play Services: permissão recusada,
   recebimento em segundo plano, logout/relogin, troca de conta, rotação e revogação.

O teste real ponta a ponta com FCM depende dessas credenciais e do novo build.
Os testes automatizados usam transporte fake ou servidor HTTP simulado, sem
credenciais reais. A integração direta desta entrega é **Android**; web push e
iOS/APNs ficam fora deste escopo. Nenhum projeto ou recurso Firebase foi criado
por este PR.

Referências: [Expo SDK 57 Notifications](https://docs.expo.dev/versions/v57.0.0/sdk/notifications/),
[FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api),
[erros e retentativas FCM](https://firebase.google.com/docs/cloud-messaging/error-codes).
