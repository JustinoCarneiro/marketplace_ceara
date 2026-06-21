-- =============================================================================
-- V1 — Schema inicial do Marketplace de Serviços Residenciais (Ceará)
-- Fonte: ROADMAP.md — Modelagem de Banco de Dados
-- Requer extensão PostGIS (índice GiST em providers_profile.localizacao, TS03).
-- LGPD: cpf_cifrado em bytea — criptografado em camada de aplicação (TS04).
-- Colunas de enum usam VARCHAR para compatibilidade com @Enumerated(EnumType.STRING).
-- =============================================================================

-- PostGIS (necessário antes de qualquer coluna geography)
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================================================
-- TABELAS CORE (M01–M07)
-- reviews → V4 | sos_alerts → V5 | dispute_resolutions → V6 | admin_notifications → V7
-- =============================================================================

-- users — base de identidade (TS04: senha nunca em texto puro, CPF cifrado)
CREATE TABLE users (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome         VARCHAR(255) NOT NULL,
    email        VARCHAR(255) NOT NULL UNIQUE,
    senha_hash   VARCHAR(255) NOT NULL,
    cpf_cifrado  BYTEA,
    role         VARCHAR(30)  NOT NULL,
    ativo        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- refresh_tokens — sessão persistente (US12)
CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revogado     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- service_categories — catálogo gerido pelo admin (US28)
CREATE TABLE service_categories (
    id     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome   VARCHAR(100) NOT NULL,
    slug   VARCHAR(100) NOT NULL UNIQUE,
    ativa  BOOLEAN      NOT NULL DEFAULT TRUE
);

-- providers_profile — perfil e reputação do prestador (US02)
CREATE TABLE providers_profile (
    id                   UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID           NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    categoria            VARCHAR(100)   NOT NULL,
    bio                  TEXT,
    localizacao          GEOGRAPHY(POINT, 4326),
    status_verificacao   VARCHAR(30)    NOT NULL DEFAULT 'EM_VERIFICACAO',
    saldo_retido         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    nota_media           NUMERIC(2, 1),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Índice GiST para geobusca p95 < 300ms (TS03)
CREATE INDEX idx_providers_localizacao ON providers_profile USING GIST (localizacao);
CREATE INDEX idx_providers_status ON providers_profile (status_verificacao);

-- background_checks — verificação assíncrona (US02)
CREATE TABLE background_checks (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id  UUID        NOT NULL REFERENCES providers_profile(id) ON DELETE CASCADE,
    status       VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    resultado    TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);

-- service_requests — pedido / chamado (coração operacional, Épico 3–7)
CREATE TABLE service_requests (
    id                    UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    cliente_id            UUID           NOT NULL REFERENCES users(id),
    prestador_id          UUID           REFERENCES users(id),
    categoria             VARCHAR(100)   NOT NULL,
    descricao             TEXT,
    status                VARCHAR(30)    NOT NULL DEFAULT 'PENDENTE',
    localizacao           GEOGRAPHY(POINT, 4326),
    ai_descricao_sugerida TEXT,
    ai_faixa_min          NUMERIC(10, 2),
    ai_faixa_max          NUMERIC(10, 2),
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_requests_status    ON service_requests (status);
CREATE INDEX idx_service_requests_cliente   ON service_requests (cliente_id);
CREATE INDEX idx_service_requests_prestador ON service_requests (prestador_id);

-- service_media — multimídia (US04, TS07) — banco só guarda URL
CREATE TABLE service_media (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id  UUID        NOT NULL REFERENCES service_requests(id) ON DELETE CASCADE,
    tipo                VARCHAR(30) NOT NULL,
    url                 TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- proposals — leilão simples (US15, US16)
CREATE TABLE proposals (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id  UUID           NOT NULL REFERENCES service_requests(id) ON DELETE CASCADE,
    prestador_id        UUID           NOT NULL REFERENCES users(id),
    valor               NUMERIC(12, 2) NOT NULL,
    prazo_dias          INTEGER        NOT NULL,
    status              VARCHAR(30)    NOT NULL DEFAULT 'ATIVA',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proposals_service ON proposals (service_request_id);

-- transactions — escrow (Épico 5, TS02)
CREATE TABLE transactions (
    id                     UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id     UUID           NOT NULL UNIQUE REFERENCES service_requests(id),
    valor_total            NUMERIC(12, 2) NOT NULL,
    valor_comissao         NUMERIC(12, 2) NOT NULL,
    percentual_comissao    NUMERIC(4, 2)  NOT NULL,
    metodo                 VARCHAR(30)    NOT NULL,
    status_pagamento       VARCHAR(30)    NOT NULL DEFAULT 'PENDENTE',
    gateway_transaction_id VARCHAR(255),
    idempotency_key        VARCHAR(255)   NOT NULL UNIQUE,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- outbox_events — motor do Saga/Outbox (TS02) — NUNCA @Transactional sobre gateway
CREATE TABLE outbox_events (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    agregado      VARCHAR(100) NOT NULL,
    agregado_id   UUID         NOT NULL,
    tipo_evento   VARCHAR(100) NOT NULL,
    payload       TEXT         NOT NULL,
    status        VARCHAR(30)  NOT NULL DEFAULT 'PENDENTE',
    tentativas    INTEGER      NOT NULL DEFAULT 0,
    criado_em     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processado_em TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox_events (status) WHERE status IN ('PENDENTE', 'FALHA');
