-- Reserva durável: sem expiração automática que permita dois workers no gateway.
ALTER TABLE outbox_events ADD COLUMN claim_token UUID,
                          ADD COLUMN processamento_em TIMESTAMPTZ;
CREATE UNIQUE INDEX uk_outbox_processando_agregado
    ON outbox_events(agregado, agregado_id) WHERE status='PROCESSANDO';
CREATE INDEX idx_outbox_pendentes_ordem ON outbox_events(criado_em,id) WHERE status='PENDENTE';
