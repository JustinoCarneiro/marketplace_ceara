package com.onda.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.onda.marketplace.servicerequest.ServiceRequestRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integração real com o Mercado Pago — Modelo A do ADR
 * {@code memoria-tecnica/decisoes/mercadopago-escrow-modelo-de-repasse.md}: a
 * plataforma recebe o valor cheio (= retido no saldo MP da plataforma) e repassa
 * {@code valor − comissão} ao prestador na conclusão.
 *
 * <p>Só substitui o {@link GatewayServiceImpl} quando
 * {@code marketplace.gateway.mercadopago.enabled=true}; o default é o stub. Como o
 * stub, é chamado SEMPRE fora de {@code @Transactional} (pelo {@link OutboxProcessor}) —
 * princípio Escrow/TS02: nada de chamada ao gateway dentro de transação de banco.
 *
 * <p><b>Estado (MKT-49):</b> {@link #cobrar} (Pix) implementado. {@link #liberar}
 * (repasse ao prestador) e {@link #reembolsar} dependem da confirmação de qual produto
 * de <i>money-out</i> o Mercado Pago habilita para a conta — enquanto isso lançam
 * {@link UnsupportedOperationException} e nenhum ambiente real liga a flag.
 */
@Service
@ConditionalOnProperty(name = "marketplace.gateway.mercadopago.enabled", havingValue = "true")
@SuppressWarnings("null")
class MercadoPagoGatewayService implements GatewayService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGatewayService.class);

    // Mercado Pago espera o offset no corpo (ex.: 2026-09-10T12:30:00.000-03:00).
    private static final DateTimeFormatter MP_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private final RestClient http;
    private final ServiceRequestRepository serviceRequestRepository;
    private final String baseUrl;
    private final String notificationUrl;
    private final int pixExpirationMinutes;

    MercadoPagoGatewayService(
            RestClient.Builder builder,
            ServiceRequestRepository serviceRequestRepository,
            @Value("${marketplace.gateway.mercadopago.base-url}") String baseUrl,
            @Value("${marketplace.gateway.mercadopago.access-token}") String accessToken,
            @Value("${marketplace.gateway.mercadopago.notification-url:}") String notificationUrl,
            @Value("${marketplace.gateway.mercadopago.pix-expiration-minutes:30}") int pixExpirationMinutes) {
        this.baseUrl = baseUrl;
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
        this.serviceRequestRepository = serviceRequestRepository;
        this.notificationUrl = notificationUrl;
        this.pixExpirationMinutes = pixExpirationMinutes;
    }

    @PostConstruct
    void avisar() {
        log.info("Gateway Mercado Pago ATIVO (base={}) — cobrança Pix real. "
                + "Repasse e reembolso ainda não implementados (MKT-49).", baseUrl);
    }

    /**
     * Cria a cobrança Pix no Mercado Pago (conta da plataforma). Devolve o
     * {@code payment.id} do MP, guardado como {@code gateway_transaction_id} e usado
     * na reconciliação por webhook.
     */
    @Override
    public String cobrar(Transaction transaction) {
        String payerEmail = serviceRequestRepository
                .findClienteEmailBySrId(transaction.getServiceRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Sem e-mail do cliente para a cobrança Pix — sr="
                                + transaction.getServiceRequestId()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", transaction.getValorTotal());
        body.put("description", "Onda — pedido " + transaction.getServiceRequestId());
        body.put("payment_method_id", "pix");
        body.put("payer", Map.of("email", payerEmail));
        // Referência estável nossa no painel/relatórios do MP. A reconciliação em si é
        // pelo gateway_transaction_id (payment.id do MP), guardado pelo OutboxProcessor.
        body.put("external_reference", transaction.getServiceRequestId().toString());
        body.put("date_of_expiration", OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(pixExpirationMinutes)
                .format(MP_DATETIME));
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }

        try {
            JsonNode res = http.post()
                    .uri("/v1/payments")
                    // Idempotência ponta a ponta: reenvio do mesmo evento do Outbox não
                    // cria cobrança duplicada no MP.
                    .header("X-Idempotency-Key", transaction.getIdempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String id = res != null ? res.path("id").asText(null) : null;
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Resposta do Mercado Pago sem id de pagamento");
            }
            log.info("Mercado Pago: cobrança Pix criada id={} status={} sr={}",
                    id, res.path("status").asText("?"), transaction.getServiceRequestId());
            return id;
        } catch (RestClientResponseException e) {
            // Propaga: o OutboxProcessor marca o evento como FALHA e re-tenta (idempotente
            // pela X-Idempotency-Key). Erro que move dinheiro nunca é engolido.
            log.warn("Mercado Pago recusou a cobrança Pix (HTTP {}) sr={}: {}",
                    e.getStatusCode().value(), transaction.getServiceRequestId(),
                    e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * Status atual do pagamento no Mercado Pago ({@code approved}, {@code pending},
     * {@code rejected}, {@code cancelled}, ...). {@code null} se a consulta falhar —
     * o webhook então não confirma nada e aguarda a próxima notificação/retry do MP.
     */
    String consultarStatusPagamento(String paymentId) {
        try {
            JsonNode res = http.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .retrieve()
                    .body(JsonNode.class);
            return res != null ? res.path("status").asText(null) : null;
        } catch (RestClientResponseException e) {
            log.warn("Mercado Pago: consulta do pagamento {} falhou (HTTP {}): {}",
                    paymentId, e.getStatusCode().value(), e.getResponseBodyAsString());
            return null;
        }
    }

    @Override
    public void liberar(Transaction transaction) {
        throw new UnsupportedOperationException(
                "Repasse Pix ao prestador ainda não implementado — depende do produto de "
                        + "money-out do Mercado Pago (ver MKT-49 e o ADR "
                        + "memoria-tecnica/decisoes/mercadopago-escrow-modelo-de-repasse.md).");
    }

    /**
     * Reembolso total do pagamento original no Mercado Pago
     * ({@code POST /v1/payments/{id}/refunds}, corpo vazio = total). Disparado pelo
     * OutboxProcessor em {@code PAYMENT_REFUNDED} (cancelamento com escrow retido ou
     * mediação a favor do cliente).
     *
     * <p>{@code X-Idempotency-Key} estável ({@code <idempotency_key>:refund}) — reenvio
     * do mesmo evento do Outbox não gera reembolso duplicado. Erro do MP propaga: o
     * evento vira {@code FALHA} e aparece na reconciliação do painel (US27).
     */
    @Override
    public void reembolsar(Transaction transaction) {
        String paymentId = transaction.getGatewayTransactionId();
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalStateException(
                    "Transação sem gateway_transaction_id — nada a reembolsar no MP (sr="
                            + transaction.getServiceRequestId() + ")");
        }
        try {
            http.post()
                    .uri("/v1/payments/{id}/refunds", paymentId)
                    .header("X-Idempotency-Key", transaction.getIdempotencyKey() + ":refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Mercado Pago: reembolso total solicitado payment={} sr={}",
                    paymentId, transaction.getServiceRequestId());
        } catch (RestClientResponseException e) {
            log.warn("Mercado Pago recusou o reembolso (HTTP {}) payment={} sr={}: {}",
                    e.getStatusCode().value(), paymentId, transaction.getServiceRequestId(),
                    e.getResponseBodyAsString());
            throw e;
        }
    }
}
