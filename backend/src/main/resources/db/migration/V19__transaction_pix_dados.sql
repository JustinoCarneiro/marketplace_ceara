-- MKT-49 (etapa 3 do ADR mercadopago-escrow-modelo-de-repasse): dados do Pix
-- (QR/copia-e-cola) devolvidos pelo Mercado Pago na criação da cobrança, para
-- exibir de verdade na tela de pagamento do cliente — antes era mockado.
--
-- Nullable de propósito: PENDENTE ainda não tem (o gateway é chamado fora do
-- @Transactional, pelo OutboxProcessor, depois que a Transaction já existe);
-- metodo=CARTAO nunca preenche.
ALTER TABLE transactions ADD COLUMN pix_qr_code VARCHAR(1000);
ALTER TABLE transactions ADD COLUMN pix_qr_code_base64 TEXT;
ALTER TABLE transactions ADD COLUMN pix_ticket_url VARCHAR(500);
