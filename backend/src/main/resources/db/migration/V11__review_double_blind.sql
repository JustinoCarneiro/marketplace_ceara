-- Avaliação double-blind (reveal simultâneo, padrão Airbnb):
-- a nota/comentário só ficam visíveis quando AMBAS as partes avaliaram, ou
-- quando expira o prazo de reciprocidade. Reduz retaliação ("te dou 1 estrela
-- porque você me deu 1") e conluio combinado entre as partes.
ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS revelada    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS revelada_em TIMESTAMPTZ;

-- Avaliações anteriores ao double-blind já eram públicas e já contam na nota
-- média dos prestadores — escondê-las retroativamente removeria reputação que
-- os usuários já viram. Ficam reveladas.
UPDATE reviews
   SET revelada = TRUE, revelada_em = criado_em
 WHERE revelada = FALSE;

-- Índice parcial: o job de revelação por prazo só varre as ainda ocultas.
CREATE INDEX IF NOT EXISTS idx_reviews_aguardando_revelacao
    ON reviews (criado_em)
    WHERE revelada = FALSE;
