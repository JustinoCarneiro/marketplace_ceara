-- Canal de denúncia (Épico 7/9): reportar prestador ou avaliação fraudulenta para moderação admin.
CREATE TABLE denuncias (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo             VARCHAR(20)  NOT NULL,
    alvo_id          UUID         NOT NULL,
    denunciante_id   UUID         NOT NULL,
    motivo           VARCHAR(60)  NOT NULL,
    detalhes         TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ABERTA',
    resolvido_por_id UUID,
    resolvido_em     TIMESTAMPTZ,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_denuncias_status ON denuncias(status);
