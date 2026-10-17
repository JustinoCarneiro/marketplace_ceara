-- US05: Suporte a pagamento via Cartão de Crédito.
-- Dados de cartão tokenizado pelo frontend (ou SDK do gateway) armazenados
-- na transação de forma compatível com PCI-DSS / LGPD: nunca armazena PAN (número
-- completo) nem CVV, apenas o token opaco temporário para cobrança, número de
-- parcelas, bandeira e os últimos 4 dígitos (card_last4) para exibição segura.

ALTER TABLE transactions ADD COLUMN card_token VARCHAR(255);
ALTER TABLE transactions ADD COLUMN parcelas INTEGER DEFAULT 1;
ALTER TABLE transactions ADD COLUMN bandeira VARCHAR(30);
ALTER TABLE transactions ADD COLUMN card_last4 VARCHAR(4);

