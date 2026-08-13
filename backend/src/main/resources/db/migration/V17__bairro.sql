-- US23 (parte 2): bairro do pedido, pra filtrar métricas/relatórios admin por região.
-- Nullable — pedidos anteriores a esta migration não têm esse dado, e o cliente escolhe
-- de uma lista fixa de bairros de Fortaleza (sem reverse geocoding implementado ainda).
ALTER TABLE service_requests ADD COLUMN bairro VARCHAR(60);

CREATE INDEX idx_service_requests_bairro ON service_requests(bairro);
