-- Chave do hash do CPF separada da chave de cifra (rotação). Até aqui o HMAC usava a MESMA chave que cifra o CPF do
-- prestador (CPF_ENCRYPTION_KEY): trocar uma invalidava a outra, e os hashes dos CLIENTES não se refazem (só o hash existe).
-- Agora o HMAC tem chave própria (CPF_HASH_KEY) e cada hash guarda a VERSÃO da chave com que foi calculado.
--   1 = chave antiga (o valor que CPF_ENCRYPTION_KEY tinha; passa a ser CPF_HASH_KEY_PREVIOUS enquanto houver conta nela)
--   2 = chave própria atual
-- Todas as contas existentes ficam na versão 1; o prestador é regravado na subida (decifra o CPF) e o cliente na próxima
-- confirmação de identidade (1º pagamento depois da troca).
ALTER TABLE users ADD COLUMN IF NOT EXISTS cpf_hash_versao INTEGER NOT NULL DEFAULT 1;
