-- Verificador por versão da chave do hash do CPF (achado da revisão cruzada, 2026-10-05): CpfHashKeyCheck só conferia a
-- VERSÃO gravada nas contas, nunca se a CHAVE configurada é a MESMA que calculou esses hashes. Trocar o valor de
-- CPF_HASH_KEY sem subir CPF_HASH_KEY_VERSION, ou informar um CPF_HASH_KEY_PREVIOUS errado, subia sem erro nenhum — e a
-- unicidade do CPF passava a comparar hashes calculados com chaves diferentes, que nunca batem.
-- O verificador é o HMAC de uma semente fixa (nunca um CPF de verdade) com a chave daquela versão: gravado na 1ª subida
-- de cada versão, conferido nas seguintes. Se a chave mudou por baixo, o verificador recalculado não bate com o gravado.
CREATE TABLE cpf_hash_key_verificacoes (
    versao      INTEGER PRIMARY KEY,
    verificador VARCHAR(64) NOT NULL
);
