-- Somente as tabelas exercitadas pelo teste JPA/H2 de atomicidade e trava.
-- Não substitui migrations nem valida o schema PostGIS dos E2E.
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    service_request_id UUID NOT NULL UNIQUE,
    valor_total NUMERIC(12,2) NOT NULL,
    valor_comissao NUMERIC(12,2) NOT NULL,
    percentual_comissao NUMERIC(12,2) NOT NULL,
    metodo VARCHAR(32) NOT NULL,
    status_pagamento VARCHAR(32) NOT NULL,
    gateway_transaction_id VARCHAR(255),
    pix_qr_code VARCHAR(1000),
    pix_qr_code_base64 TEXT,
    pix_ticket_url VARCHAR(500),
    idempotency_key VARCHAR(255) NOT NULL,
    card_token VARCHAR(255),
    parcelas INTEGER DEFAULT 1,
    bandeira VARCHAR(30),
    card_last4 VARCHAR(4),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    agregado VARCHAR(255) NOT NULL,
    agregado_id UUID NOT NULL,
    tipo_evento VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    tentativas INTEGER NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    claim_token UUID,
    processamento_em TIMESTAMP WITH TIME ZONE,
    processado_em TIMESTAMP WITH TIME ZONE
);
CREATE TABLE admin_audit_log (
    id UUID PRIMARY KEY,
    admin_id UUID NOT NULL,
    admin_nome VARCHAR(255) NOT NULL,
    acao VARCHAR(60) NOT NULL,
    entidade VARCHAR(60) NOT NULL,
    entidade_id UUID,
    detalhe TEXT,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
