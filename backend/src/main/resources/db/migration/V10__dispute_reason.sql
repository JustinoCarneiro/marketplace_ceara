-- Motivo/detalhes informados por quem abre a disputa (cliente ou prestador).
-- Antes só existia o status EM_DISPUTA — o mediador via QUE havia disputa, não POR QUÊ.
ALTER TABLE service_requests
    ADD COLUMN IF NOT EXISTS motivo_disputa VARCHAR(255),
    ADD COLUMN IF NOT EXISTS detalhes_disputa TEXT;
