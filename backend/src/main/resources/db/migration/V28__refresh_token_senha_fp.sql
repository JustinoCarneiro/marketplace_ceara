-- Sessão emitida em voo sobrevivia à troca de senha (achado da rodada 3 de revisão cruzada, 2026-10-10).
-- A troca encerra as sessões com um UPDATE (revogarTodosDoUsuario) que só enxerga as linhas JÁ commitadas no instante dele. Uma
-- renovação ou um login em voo — que consumiu o token antigo e insere o novo, sem ter commitado — faz o UPDATE esperar a linha antiga
-- e, ao acordar, NÃO ver a linha nova: o token novo sobrevivia a uma troca de senha feita justamente para encerrar sessões.
--
-- senha_fp = impressão da senha (sha256 do hash bcrypt, truncado) que existia quando a sessão foi emitida. O consumo do refresh token
-- (renovar, trocar de papel, virar prestador) recusa o token cuja impressão não bate com a senha ATUAL da conta; assim o token que
-- nasce com a senha antiga já nasce inválido, não importa a ordem das transações. NULL = sessão anterior a esta migration: continua
-- valendo, para o deploy não deslogar ninguém (essas sessões seguem sujeitas à corrida até expirarem, em no máximo 30 dias).
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS senha_fp VARCHAR(32);
