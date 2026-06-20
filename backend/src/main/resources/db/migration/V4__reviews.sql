-- M08: avaliação bidirecional cliente ↔ prestador
CREATE TABLE reviews (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id UUID         NOT NULL REFERENCES service_requests(id),
    avaliador_id       UUID         NOT NULL,
    avaliado_id        UUID         NOT NULL,
    tipo               VARCHAR(50)  NOT NULL,
    nota               INT          NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario         TEXT,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (service_request_id, tipo)
);

CREATE INDEX idx_reviews_avaliado_tipo ON reviews (avaliado_id, tipo);
