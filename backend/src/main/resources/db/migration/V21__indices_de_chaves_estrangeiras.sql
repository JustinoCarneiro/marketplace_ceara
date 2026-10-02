-- Índices nas chaves estrangeiras que o app realmente consulta.
--
-- O Postgres indexa a PK e o UNIQUE, mas NÃO a coluna que referencia (FK). Sem índice, cada
-- consulta por essa coluna lê a tabela inteira. Auditoria no catálogo (pg_constraint x pg_index)
-- achou 7 FKs sem índice; só estas 3 têm leitura no código:
--
--   proposals.prestador_id          ProposalRepository (findByPrestadorId[AndStatus], countBy...)
--   refresh_tokens.user_id          RefreshTokenRepository: revogar todas as sessões do usuário
--                                   (troca de senha, suspensão da conta)
--   service_media.service_request_id  ServiceMediaRepository: mídia do pedido
--
-- Ficam de fora, de propósito, as 4 que nenhuma consulta usa como filtro — um índice só
-- custaria escrita: sos_alerts.service_request_id, background_checks.provider_id,
-- admin_audit_log.admin_id (log só de inserção) e messages.remetente_id.
CREATE INDEX idx_proposals_prestador ON proposals (prestador_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_service_media_service_request ON service_media (service_request_id);
