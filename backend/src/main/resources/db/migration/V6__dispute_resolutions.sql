-- M10: trilha de auditoria imutável (append-only) das resoluções de disputa pela mediação (US22/US24/TS09)
CREATE TABLE dispute_resolutions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id UUID         NOT NULL UNIQUE REFERENCES service_requests(id),
    admin_id           UUID         NOT NULL,
    decisao            VARCHAR(20)  NOT NULL,
    justificativa      TEXT,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- A constraint UNIQUE acima já cria o índice em service_request_id.
