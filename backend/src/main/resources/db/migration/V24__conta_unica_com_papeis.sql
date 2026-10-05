-- Conta única com papéis (antifraude, Camada 3): uma pessoa tem UMA conta e ela pode ser cliente e prestador, alternando
-- de contexto (modelo Uber/Airbnb). Contratar a si mesmo vira impossível por construção — é sempre o mesmo user_id — e o
-- CPF volta a ser único na tabela inteira (users.cpf_hash UNIQUE, V9).
--
-- users.role passa a ser o papel PRINCIPAL (o do cadastro; é o contexto em que o login abre). Os papéis que a conta
-- TEM ficam aqui. O papel em uso numa sessão vem no token (claim "role") e é trocado por POST /auth/switch-role.
CREATE TABLE user_papeis (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    papel   VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, papel)
);

-- cada conta tem o papel com que foi cadastrada...
INSERT INTO user_papeis (user_id, papel) SELECT id, role FROM users;
-- ...e todo prestador também pode contratar (o prestador que precisa de um eletricista em casa)
INSERT INTO user_papeis (user_id, papel) SELECT id, 'ROLE_CLIENT' FROM users WHERE role = 'ROLE_PROVIDER';

-- O contexto da sessão acompanha o refresh token: renovar a sessão não pode jogar o usuário de volta ao papel principal.
-- NULL = sessão anterior a esta migration (renova no papel principal).
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS papel VARCHAR(30);
