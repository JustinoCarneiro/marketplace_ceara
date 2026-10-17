package com.onda.marketplace.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionDto(
        UUID       id,
        UUID       serviceRequestId,
        BigDecimal valorTotal,
        BigDecimal valorComissao,
        String     metodo,
        String     statusPagamento,
        Instant    createdAt,
        // QR/copia-e-cola do Pix — null até o OutboxProcessor confirmar com o gateway
        // (MercadoPagoGatewayService.cobrar()) e sempre null para metodo=CARTAO.
        String     pixQrCode,
        String     pixQrCodeBase64,
        String     pixTicketUrl,
        Integer    parcelas,
        String     bandeira,
        String     cardLast4
) {
    public TransactionDto(
            UUID id, UUID serviceRequestId, BigDecimal valorTotal, BigDecimal valorComissao,
            String metodo, String statusPagamento, Instant createdAt,
            String pixQrCode, String pixQrCodeBase64, String pixTicketUrl) {
        this(id, serviceRequestId, valorTotal, valorComissao, metodo, statusPagamento,
             createdAt, pixQrCode, pixQrCodeBase64, pixTicketUrl, 1, null, null);
    }

    public static TransactionDto from(Transaction t) {
        return new TransactionDto(
                t.getId(), t.getServiceRequestId(), t.getValorTotal(),
                t.getValorComissao(), t.getMetodo().name(),
                t.getStatusPagamento().name(), t.getCreatedAt(),
                t.getPixQrCode(), t.getPixQrCodeBase64(), t.getPixTicketUrl(),
                t.getParcelas(), t.getBandeira(), t.getCardLast4());
    }
}

