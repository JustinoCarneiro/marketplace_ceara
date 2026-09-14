package com.onda.marketplace.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida a assinatura {@code x-signature} das notificações do Mercado Pago.
 *
 * <p>O header chega como {@code ts=<unix>,v1=<hmac-hex>}. O MP assina o template
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} com HMAC-SHA256 usando o
 * segredo da aplicação (segmentos ausentes são removidos inteiros, com o
 * {@code ;}). IDs alfanuméricos entram em minúsculo — o de pagamento é numérico,
 * então é indiferente.
 *
 * <p>Só existe como bean quando {@code marketplace.gateway.mercadopago.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "marketplace.gateway.mercadopago.enabled", havingValue = "true")
class MercadoPagoWebhookVerifier {

    private final byte[] secret;

    MercadoPagoWebhookVerifier(
            @Value("${marketplace.gateway.mercadopago.webhook-secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return {@code true} só quando a assinatura confere com o segredo configurado.
     *         Sem segredo, sem header ou sem {@code data.id} → {@code false}.
     */
    boolean valido(String xSignature, String xRequestId, String dataId) {
        if (secret.length == 0 || xSignature == null || dataId == null || dataId.isBlank()) {
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String k = part.substring(0, eq).trim();
            String v = part.substring(eq + 1).trim();
            if (k.equals("ts")) ts = v;
            else if (k.equals("v1")) v1 = v;
        }
        if (ts == null || v1 == null || v1.isEmpty()) return false;

        StringBuilder manifest = new StringBuilder("id:")
                .append(dataId.toLowerCase()).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] calculado = mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8));
            byte[] recebido = HexFormat.of().parseHex(v1);
            return MessageDigest.isEqual(calculado, recebido);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false; // v1 não-hex ou HMAC indisponível
        }
    }
}
