package com.onda.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe as notificações (webhooks) do Mercado Pago — o caminho de produção que
 * substitui o {@link SimulatedGatewayCallback}.
 *
 * <p>Só ativo com {@code marketplace.gateway.mercadopago.enabled=true}. A rota é
 * liberada sem autenticação no {@code SecurityConfig}; a autenticidade vem da
 * assinatura {@code x-signature} ({@link MercadoPagoWebhookVerifier}).
 *
 * <p>O estado financeiro continua dirigido por evento confirmado
 * ({@link PaymentService#confirmPayment}) — mesmo caminho do endpoint legado
 * {@code POST /api/v1/payments/webhook}, que segue existindo pros testes e pro
 * gateway simulado.
 */
@RestController
@RequestMapping("/api/v1/payments/mercadopago")
@ConditionalOnProperty(name = "marketplace.gateway.mercadopago.enabled", havingValue = "true")
@SuppressWarnings("null")
class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoWebhookVerifier verifier;
    private final MercadoPagoGatewayService gateway;
    private final PaymentService paymentService;

    MercadoPagoWebhookController(MercadoPagoWebhookVerifier verifier,
                                MercadoPagoGatewayService gateway,
                                PaymentService paymentService) {
        this.verifier = verifier;
        this.gateway = gateway;
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    ResponseEntity<Void> webhook(
            @RequestBody(required = false) JsonNode body,
            @RequestParam(name = "type", required = false) String typeParam,
            @RequestParam(name = "data.id", required = false) String dataIdParam,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId) {

        JsonNode json = body != null ? body : com.fasterxml.jackson.databind.node.NullNode.getInstance();
        String type   = firstNonBlank(json.path("type").asText(null), typeParam);
        String dataId = firstNonBlank(json.path("data").path("id").asText(null), dataIdParam);

        if (dataId == null || !verifier.valido(signature, requestId, dataId)) {
            log.warn("Webhook Mercado Pago recusado: assinatura inválida ou sem data.id (type={})", type);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // merchant_order, chargebacks, etc. — reconhece e ignora (200 evita retry do MP).
        if (!"payment".equals(type)) {
            return ResponseEntity.ok().build();
        }

        String mpStatus = gateway.consultarStatusPagamento(dataId);
        String interno = switch (mpStatus == null ? "" : mpStatus) {
            case "approved" -> "PAGO";
            case "rejected", "cancelled" -> "REJEITADO";
            default -> null; // pending / in_process / authorized: aguarda próxima notificação
        };

        if (interno != null) {
            // confirmPayment resolve a transação por gateway_transaction_id (o payment.id
            // do MP guardado em cobrar); pagamento desconhecido é no-op silencioso.
            paymentService.confirmPayment(dataId, interno);
            log.info("Webhook Mercado Pago: pagamento {} → {} (interno {})", dataId, mpStatus, interno);
        }
        return ResponseEntity.ok().build();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
