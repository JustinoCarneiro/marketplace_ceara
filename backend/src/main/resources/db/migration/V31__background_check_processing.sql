-- Fila durável de verificação. Sem dados pessoais adicionais ou laudos do fornecedor.
ALTER TABLE background_checks ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE background_checks ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE background_checks ADD COLUMN claim_token UUID;
CREATE INDEX idx_background_checks_pending ON background_checks (available_at, requested_at)
    WHERE status IN ('PENDENTE', 'PROCESSANDO');
