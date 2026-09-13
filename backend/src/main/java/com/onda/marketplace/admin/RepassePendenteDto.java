package com.onda.marketplace.admin;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dados pro operador pagar manualmente o repasse ao prestador — enquanto o Mercado
 * Pago não expõe payout Pix programático pra conta (MKT-49/MKT-50). A chave Pix vem
 * decifrada só aqui, numa consulta ROLE_ADMIN sob demanda; nunca é logada e nenhum
 * outro DTO a expõe.
 */
public record RepassePendenteDto(
        UUID transactionId,
        UUID serviceRequestId,
        UUID prestadorId,
        String prestadorNome,
        BigDecimal valorARepassar,
        boolean chavePixCadastrada,
        String chavePix
) {}
