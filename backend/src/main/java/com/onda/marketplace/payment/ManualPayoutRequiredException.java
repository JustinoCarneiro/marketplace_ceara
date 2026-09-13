package com.onda.marketplace.payment;

import java.util.UUID;

/**
 * Sinaliza que o repasse ao prestador não pôde ser automatizado — hoje porque o
 * Mercado Pago ainda não expõe payout/transferência Pix programática pra conta da
 * plataforma (MKT-49/MKT-50 — deve passar a existir; ver ADR
 * {@code memoria-tecnica/decisoes/mercadopago-escrow-modelo-de-repasse.md}).
 *
 * <p>Tratada pelo {@link OutboxProcessor} como qualquer outra falha: o evento vira
 * {@code FALHA} e aparece na fila de reconciliação do admin. A diferença é que o
 * "reprocessar" padrão (volta pra {@code PENDENTE} e chama {@link GatewayService#liberar}
 * de novo) não resolve nada aqui — precisa da ação dedicada
 * {@code POST /admin/transactions/{id}/confirmar-repasse-manual}, depois que o operador
 * paga o prestador fora do sistema (chave Pix visível em
 * {@code GET /admin/transactions/{id}/repasse-pendente}).
 */
public class ManualPayoutRequiredException extends RuntimeException {

    public ManualPayoutRequiredException(UUID serviceRequestId) {
        super("Repasse automático indisponível para o pedido " + serviceRequestId
                + " — Mercado Pago sem payout Pix programático nesta conta ainda. "
                + "Use a fila de repasse manual no painel admin.");
    }
}
