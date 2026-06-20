-- M09: alerta de emergência (Botão SOS)
CREATE TABLE sos_alerts (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    service_request_id UUID         REFERENCES service_requests(id),
    latitude           NUMERIC(10,7),
    longitude          NUMERIC(10,7),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ATIVO',
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolvido_em       TIMESTAMPTZ
);

CREATE INDEX idx_sos_alerts_status ON sos_alerts (status) WHERE status = 'ATIVO';
