-- Registro durável na transação de exclusão da conta; não depende da disponibilidade do S3/R2.
CREATE TABLE storage_deletions (
    id UUID PRIMARY KEY,
    reference VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_storage_deletions_created_at ON storage_deletions (created_at);
