-- Trilha de auditoria de ações administrativas (US22 / TS09) — append-only, imutável.
-- Captura quem (admin_id + snapshot do nome), o quê (acao + entidade[/id]) e quando.
CREATE TABLE admin_audit_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id     UUID         NOT NULL REFERENCES users(id),
    admin_nome   VARCHAR(255) NOT NULL,
    acao         VARCHAR(60)  NOT NULL,
    entidade     VARCHAR(60)  NOT NULL,
    entidade_id  UUID,
    detalhe      TEXT,
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_log_criado_em ON admin_audit_log (criado_em DESC);
