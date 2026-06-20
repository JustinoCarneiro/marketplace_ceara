-- Idempotência na criação de pedidos (CLAUDE.md — princípio de idempotência)
ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255) UNIQUE;
