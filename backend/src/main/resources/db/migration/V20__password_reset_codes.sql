-- US35 — Recuperação de senha: código de uso único enviado por e-mail.
--
-- O código NUNCA é gravado em claro: só o HMAC-SHA256 dele, com o usuário no cálculo
-- (um dump do banco não entrega códigos válidos). "Ativo" = closed_at IS NULL, dentro da
-- validade e com menos tentativas erradas que o limite — a regra está em PasswordResetService.
--
-- closed_at fecha o código por qualquer motivo: usado, substituído por um pedido novo ou
-- invalidado por excesso de tentativas. As linhas ficam (não são apagadas) porque o limite
-- de pedidos por hora conta as emitidas, abertas ou não.
CREATE TABLE password_reset_codes (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    closed_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_codes_user_created
    ON password_reset_codes (user_id, created_at DESC);
