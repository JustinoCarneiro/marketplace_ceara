-- Limite de tentativas de senha (login e exclusão de conta): o contador e o bloqueio ficam na conta.
-- Zerar o contador no acerto ou na redefinição de senha; passado `senha_bloqueada_ate`, recomeça do zero.
-- NOT NULL DEFAULT 0 num ALTER é instantâneo no PostgreSQL 11+ (não reescreve a tabela).
ALTER TABLE users
    ADD COLUMN senha_falhas         INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN senha_bloqueada_ate  TIMESTAMPTZ;
