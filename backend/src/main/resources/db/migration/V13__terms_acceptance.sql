-- Registro de aceite dos Termos de Uso/Privacidade no cadastro (docs/PENDENCIAS_JURIDICAS.md
-- item 3) — prova de consentimento informado (quem, qual versão do documento, quando, de onde).
-- Append-only: sem updates, cada novo aceite (ex.: nova versão do documento) gera uma nova linha.
CREATE TABLE terms_acceptance (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id),
    doc_version VARCHAR(30)  NOT NULL,
    accepted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_address  VARCHAR(45)  NOT NULL
);

CREATE INDEX idx_terms_acceptance_user_id ON terms_acceptance (user_id);
