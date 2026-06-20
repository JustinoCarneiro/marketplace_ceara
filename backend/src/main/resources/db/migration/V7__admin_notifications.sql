-- M12: central de notificações do painel admin (US30)
-- Persiste alertas SOS | DISPUTA | VERIFICACAO para leitura posterior pelo admin.
-- Nunca expõe CPF — apenas UUID de referência ao registro de origem (TS04/LGPD).
CREATE TABLE admin_notifications (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo       VARCHAR(30) NOT NULL,   -- SOS | DISPUTA | VERIFICACAO
    ref_id     UUID        NOT NULL,   -- ID do sos_alert, service_request, ou provider_profile
    lida       BOOLEAN     NOT NULL DEFAULT false,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Índice para filtro por lida (listagem do painel, polling periódico)
CREATE INDEX idx_admin_notifications_lida ON admin_notifications(lida);
