-- MKT-49 (Modelo A do ADR mercadopago-escrow-modelo-de-repasse): chave Pix do
-- prestador, destino do repasse na conclusão do serviço.
--
-- PII/LGPD: cifrada em repouso na camada de aplicação (AES-GCM, mesma chave do
-- cpf_cifrado — ver CpfEncryptor). Nunca trafega em claro em DTO nem em log.
--
-- Nullable de propósito: perfis criados antes desta migration e prestadores que
-- ainda não cadastraram a chave. O repasse (GatewayService.liberar) exige a chave
-- presente; sem ela o repasse vira pendência operacional no painel admin.
ALTER TABLE providers_profile ADD COLUMN chave_pix_cifrada VARCHAR(512);
