-- Chave de idempotência era UNIQUE GLOBAL, não por dono — semântica errada e vazamento real:
-- o app gerava a chave só com o timestamp em milissegundos, então dois clientes criando
-- pedido no mesmo milissegundo colidiam e o segundo recebia de volta o pedido do primeiro
-- (id + descrição), porque o caminho de cache-hit devolvia o registro sem conferir posse.
--
-- Idempotência é por solicitante (padrão de mercado): a mesma chave só deve reaproveitar o
-- resultado de quem a enviou. Escopo agora é composto.

ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS service_requests_idempotency_key_key;
ALTER TABLE service_requests
    ADD CONSTRAINT uk_service_requests_cliente_idempotency
    UNIQUE (cliente_id, idempotency_key);

-- Pagamento é sempre por pedido (POST /service-requests/{id}/payment) e a posse do pedido já
-- é verificada — escopar por service_request_id dá a mesma garantia sem coluna nova.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_idempotency_key_key;
ALTER TABLE transactions
    ADD CONSTRAINT uk_transactions_request_idempotency
    UNIQUE (service_request_id, idempotency_key);
