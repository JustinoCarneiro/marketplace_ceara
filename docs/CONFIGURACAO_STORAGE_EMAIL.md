# Armazenamento privado e e-mail transacional

Implementação M17 preparada para AWS S3 ou Cloudflare R2 e SMTP com STARTTLS (porta 587).
A ativação depende das contas externas e, para e-mail aos usuários, de um domínio próprio
verificado. Enquanto isso, a demo `seed` usa armazenamento simulado e mantém o aviso
explícito de ausência de alertas externos. Não é uma configuração de produção real.

## Criar o armazenamento antes de ter domínio

1. Entre no painel da Cloudflare, ative R2 e confira as condições de cobrança antes de confirmar.
2. Crie um bucket **privado**, por exemplo `marketplace-ceara-media`. Não habilite acesso público
   por `r2.dev` nem domínio público para esse bucket.
3. Crie um token S3 com **Object Read & Write**, limitado a esse bucket. Essa permissão também
   permite remover arquivos na exclusão de conta. Guarde Access Key ID e Secret Access Key
   diretamente nas variáveis protegidas do Coolify; não os envie por chat ou Git.
4. Configure o backend:

```dotenv
STORAGE_PROVIDER=s3
STORAGE_BUCKET=marketplace-ceara-media
STORAGE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
STORAGE_REGION=auto
STORAGE_ACCESS_KEY=<inserir-no-ambiente-protegido>
STORAGE_SECRET_KEY=<inserir-no-ambiente-protegido>
STORAGE_READ_URL_TTL_SECONDS=600
```

Para AWS S3, deixe `STORAGE_ENDPOINT` vazio e informe a região do bucket. A identidade precisa
apenas de GetObject, PutObject e DeleteObject no prefixo `service-requests/*` do bucket.
As credenciais são explícitas; não há descoberta automática de credenciais AWS da máquina.

Referências: [R2/S3](https://developers.cloudflare.com/r2/get-started/s3/),
[tokens](https://developers.cloudflare.com/r2/api/tokens/),
[preços](https://developers.cloudflare.com/r2/pricing/).

## Criar o remetente quando houver domínio

1. Escolha e registre um domínio em nome do responsável pelo projeto. A URL `sslip.io` da demo
   não pertence ao projeto e não permite verificar o domínio para envio.
2. Crie uma conta no Resend e adicione o domínio ou um subdomínio de envio.
3. Cadastre no DNS os registros de verificação informados pelo Resend e aguarde a verificação.
4. Crie uma chave restrita a envio e, se disponível, ao domínio escolhido.
5. Configure as variáveis protegidas do backend:

```dotenv
MAIL_HOST=smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=resend
MAIL_PASSWORD=<chave-inserida-no-ambiente-protegido>
MAIL_FROM=contato@<dominio-verificado>
ADMIN_ALERT_EMAIL=<caixa-monitorada-pela-equipe>
ALLOW_MISSING_ALERT_CHANNEL=false
```

O login `resend` autentica a conexão; `MAIL_FROM` é o endereço verificado que aparece na mensagem.
O fallback para `MAIL_USERNAME` preserva provedores cujo login já é um endereço de e-mail.
Usuário sem senha ou remetente inválido não habilitam o canal. A ausência de canal continua
barrando a inicialização quando `ALLOW_MISSING_ALERT_CHANNEL=false`.
O backend exige STARTTLS e usa limites de espera de 10 segundos no SMTP.

Não configure `NOTIFICATION_MAIL_SINK_DIR` em produção: esse recurso de desenvolvimento grava
mensagens de recuperação de senha em disco. Conta, DNS e credenciais não foram provisionados por
esta implementação; não houve envio externo de mensagens.

Referências: [SMTP Resend](https://resend.com/docs/send-with-smtp),
[verificação de domínio](https://resend.com/docs/dashboard/domains/introduction),
[planos](https://resend.com/pricing).

## Contrato e validação após ativar

- `POST /api/v1/service-requests/{id}/media` mantém multipart (`file`, `tipo`) e exige cliente
  participante antes do upload. Limite de arquivo: 15 MB, conforme o servidor.
- O banco guarda `s3://bucket/service-requests/<pedido>/<objeto>`, sem nome original ou segredo.
  A resposta mantém `{id, url, tipo}`, com URL GET assinada válida por 10 minutos por padrão.
- `GET /api/v1/service-requests/{id}/media` renova os links para participantes (cliente ou
  prestador aceito); responde com `Cache-Control: no-store`. O detalhe administrativo da disputa
  também gera links novos. As URLs são credenciais temporárias de leitura: não publicar ou registrar.
- Validade configurável de 60 a 900 segundos. O bucket continua privado; quem possuir um link
  assinado pode ler o objeto até ele expirar. Não há revogação instantânea por logout.
- Falha do armazenamento retorna HTTP 503 / `STORAGE_UNAVAILABLE`, sem registrar mídia no banco.
  URLs antigas permanecem compatíveis; arquivos que o antigo stub nunca salvou não são recuperáveis.
- Na exclusão de conta, as referências reais são enfileiradas na mesma transação que remove
  as mídias do banco. Após o commit, o worker remove até 50 objetos por ciclo (padrão: 60 segundos).
  Falha conserva o trabalho para nova tentativa; DeleteObject é idempotente. A fila não é processada
  por um storage simulado. Monitore a quantidade e a idade dos registros de `storage_deletions`.
- Falha entre upload no bucket e commit do pedido pode deixar objeto órfão. Antes de receber dados
  reais, definir reconciliação por inventário do bucket contra referências do banco, retenção e
  procedimento de remoção. Não aplicar expiração indiscriminada a todo o prefixo: apagaria mídia ativa.
- Em bucket versionado, excluir o objeto atual pode conservar versões anteriores; definir também
  a política de remoção de versões, réplicas e backups antes de receber dados reais.

A validação local usa o SDK real contra servidor HTTP S3 controlado (bytes, Content-Type, geração da assinatura,
falha e DeleteObject), além de testes de autorização, configuração SMTP e transações da fila.
Isso não substitui um teste com as contas efetivas:

1. Publicar em ambiente de teste com bucket e remetente verificados.
2. Enviar foto/áudio e conferir os bytes no download assinado; acesso anônimo sem assinatura deve falhar.
3. Conferir expiração e renovação do link e recusa para usuário fora do pedido.
4. Excluir conta de teste e conferir remoção física e esvaziamento da fila, inclusive após falha simulada.
5. Testar recuperação de senha e um alerta operacional para uma caixa controlada; conferir entrega,
   remetente, pasta de spam e estado do outbox. Nunca usar SOS real para esse teste.
6. Manter a demo sem dados reais até concluir as validações operacionais e jurídicas pendentes.

Validação desta entrega: `mvn test` — 702 testes reportados, 1 ignorado, sem falhas/erros;
`mvn -DskipTests package` e validação dos dois arquivos Compose concluídos.
O E2E com PostGIS depende do CI: Docker indisponível nesta máquina.
