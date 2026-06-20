-- CPF cifrado (AES-256-GCM, base64) no perfil do prestador (LGPD — TS04)
ALTER TABLE providers_profile
    ADD COLUMN IF NOT EXISTS cpf_cifrado VARCHAR(500);
