CREATE TABLE push_devices (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE, token_cipher TEXT NOT NULL,
 binding UUID NOT NULL, revocation_hash VARCHAR(64) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_push_devices_user ON push_devices(user_id);
CREATE TABLE push_deliveries (
 id UUID PRIMARY KEY, device_id UUID NOT NULL REFERENCES push_devices(id) ON DELETE CASCADE,
 binding UUID NOT NULL, kind VARCHAR(40) NOT NULL, reference_id UUID NOT NULL,
 status VARCHAR(15) NOT NULL DEFAULT 'PENDING', attempts INTEGER NOT NULL DEFAULT 0,
 claim_token UUID, available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(device_id,binding,kind,reference_id)
);
CREATE INDEX idx_push_deliveries_pending ON push_deliveries(available_at) WHERE status='PENDING';

-- Reservas só nascem autenticadas. A revogação pública nunca cria registros.
CREATE TABLE push_registration_fences (
 capability_hash VARCHAR(64) PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 expires_at TIMESTAMPTZ NOT NULL DEFAULT NOW()+INTERVAL '5 minutes',
 revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_push_registration_fences_user ON push_registration_fences(user_id);
CREATE INDEX idx_push_registration_fences_created ON push_registration_fences(created_at);
