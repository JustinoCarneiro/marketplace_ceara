# Reserva exclusiva da fila financeira

## Contrato

A migração V33 acrescenta `claim_token` e `processamento_em` à outbox.
O worker reserva **um evento de cada vez** em transação curta: trava a Transaction
com `FOR UPDATE SKIP LOCKED`, relê o evento e grava `PROCESSANDO` com UUID próprio.
O índice parcial único impede dois eventos em processamento para o mesmo agregado.
Outro worker pode atender transações diferentes enquanto uma está ocupada.

O gateway é chamado fora da transação de banco. Ao concluir, o writer trava
Transaction e OutboxEvent nessa ordem e só aceita o resultado do claim atual.
Na cobrança, copia exclusivamente os campos devolvidos pelo gateway sobre a
Transaction relida; não faz merge do snapshot que precedeu uma confirmação por webhook.
O webhook também trava a Transaction. Repasse e reembolso validam o estado financeiro
antes do envio e aplicam a transição sobre o estado atual na conclusão.

Se o estado de destino já foi atingido, o worker encerra o evento sem repetir o
repasse/reembolso. Falhas do gateway ficam em `FALHA`, com contador incrementado;
o retry continua uma ação administrativa. As chaves de idempotência existentes
para cobrança e reembolso permanecem estáveis.

SOS tem prioridade sobre eventos financeiros e política diferente: um `SOS_TRIGGERED` não financeiro reservado há mais
de cinco minutos pode ser retomado automaticamente com novo claim. Uma entrega
anterior ainda em trânsito pode duplicar o alerta; o resultado antigo não conclui
o claim novo. Essa retomada preserva a durabilidade do alerta e não vale para dinheiro.

O admin recusa reenfileiramento e confirmação de repasse manual enquanto houver
uma operação em processamento para a mesma transação (`OUTBOX_IN_PROGRESS`).
O painel lista separadamente esses eventos e a data/hora de início, sem oferecer
retry ou repasse manual para o agregado ocupado. `PROCESSANDO` também pode ser
consultado por `GET /api/v1/admin/outbox?status=PROCESSANDO`.

## Interrupção e reconciliação

**Não há expiração automática do claim financeiro.** Um relógio vencido não prova que o worker
anterior deixou de chamar o gateway. Reenviar automaticamente nessa condição
poderia sobrepor uma operação financeira ainda em curso.

Se o processo cair depois da reserva financeira, ou falhar ao persistir o resultado depois
do envio, o evento permanece `PROCESSANDO`, visível no painel. Isso pode exigir
intervenção operacional; reiniciar o serviço não libera a reserva.

Para resolver um evento interrompido:

1. Identificar o evento e a transação no painel; confirmar que a instância anterior
   foi encerrada e que não há chamada ainda em curso.
2. Conferir o resultado no provedor usando a referência e a chave de idempotência
   originais. Não interpretar timeout ou ausência de confirmação local como falha externa.
3. Registrar a evidência e reconciliar estado financeiro/evento em uma intervenção
   controlada, revisada e autorizada. Se o resultado for desconhecido, manter a reserva.
4. Somente reenfileirar uma operação cuja repetição tenha sido validada com o
   contrato de idempotência do provedor, preservando a chave original.

Não foi criado botão de desbloqueio cego nem reconciliação automática com provedor.
A garantia é exclusão local entre workers e proteção da gravação por claim;
uma chamada externa seguida de queda ainda exige idempotência/reconciliação
no provedor. A fila não promete execução externa exatamente uma vez.

## Publicação

Publicar com os workers antigos encerrados antes de iniciar os novos: o código
anterior não participa do protocolo de reserva. Não usar rolling deployment misto.
Antes de voltar ao código anterior, pausar os workers e resolver todos os claims
`PROCESSANDO`; a versão antiga não conhece esse estado. A migração é aditiva,
mas o protocolo de processamento precisa ser respeitado no rollback.

## Validação

- Regressão de cobrança: preserva o estado confirmado pelo webhook e os dados do gateway.
- PostGIS real: dois workers, eventos financeiros distintos do mesmo agregado,
  bloqueio de intervenções administrativas, SKIP LOCKED entre agregados, claim
  obsoleto, falha de persistência após envio, webhook concorrente, retry com chave
  estável e reserva interrompida sem expiração.
- Gateway dos testes verifica ausência de transação de banco na chamada externa.
- Interface: operação em processamento visível, sem retry/repasse concorrente.
