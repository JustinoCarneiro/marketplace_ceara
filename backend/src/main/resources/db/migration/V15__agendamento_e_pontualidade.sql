-- Agendamento (US15): prestador propõe uma data/hora junto com o preço na proposta.
-- Nullable porque propostas já existentes (antes desta migration) não têm esse dado.
ALTER TABLE proposals ADD COLUMN horario_proposto TIMESTAMPTZ;

-- Pontualidade (US03): quando o pedido de fato entra em EM_ANDAMENTO, comparado contra
-- o horario_proposto da proposta aceita. Nullable pelo mesmo motivo, e porque nem todo
-- pedido chega a ser iniciado (pode ser cancelado antes).
ALTER TABLE service_requests ADD COLUMN iniciado_em TIMESTAMPTZ;
