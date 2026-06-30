-- CPF hash determinístico (HMAC-SHA256) para unicidade por pessoa (antifraude Camada 2)
-- LGPD: somente o hash é indexado — o CPF em claro nunca entra no banco para clientes.
-- Clientes preenchem o CPF no primeiro pagamento; prestadores têm o CPF cifrado em providers_profile.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS cpf_hash VARCHAR(64) UNIQUE;
