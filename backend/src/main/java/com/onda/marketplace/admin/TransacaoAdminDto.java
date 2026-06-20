package com.onda.marketplace.admin;

import com.onda.marketplace.payment.TransactionStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Visão de transação para reconciliação financeira no painel admin (US27). */
public record TransacaoAdminDto(
        UUID id,
        UUID serviceRequestId,
        BigDecimal valorTotal,
        TransactionStatus statusPagamento
) {}
