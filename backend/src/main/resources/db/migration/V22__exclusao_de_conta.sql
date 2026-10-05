-- US36 — Exclusão de conta pelo app (LGPD, art. 18 VI; exigência da App Store e da Play Store).
--
-- Não se apaga a linha de `users`: service_requests.cliente_id, proposals.prestador_id,
-- messages.remetente_id e terms_acceptance.user_id apontam para ela sem ON DELETE CASCADE, e o aceite de
-- termos é imutável por desenho (prova do consentimento). A conta é ANONIMIZADA no lugar — nome, e-mail,
-- senha e CPF saem — e esta coluna marca quando. Preenchida = conta excluída (e não reativável).
ALTER TABLE users ADD COLUMN excluido_em TIMESTAMPTZ;
