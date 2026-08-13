-- Chat pré-transação entre cliente e prestador (docs/BOAS_PRATICAS_UX.md §1) — histórico de
-- mensagens por pedido, com sinalização de quando o conteúdo foi mascarado (telefone/e-mail
-- removidos, anti-desintermediação: impedir combinar pagamento fora da plataforma).
CREATE TABLE messages (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_request_id UUID NOT NULL REFERENCES service_requests(id),
    remetente_id       UUID NOT NULL REFERENCES users(id),
    conteudo           TEXT NOT NULL,
    mascarado          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Toda leitura de chat é "mensagens do pedido X, mais recentes por último" — índice cobre o
-- caso de uso único da tela.
CREATE INDEX idx_messages_service_request ON messages(service_request_id, created_at);
