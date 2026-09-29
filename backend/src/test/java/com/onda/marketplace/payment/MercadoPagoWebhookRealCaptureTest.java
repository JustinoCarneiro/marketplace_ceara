package com.onda.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida notificações REAIS do Mercado Pago (capturadas em JSONL: headers, query,
 * corpo) contra o {@link MercadoPagoWebhookVerifier} de produção. Os testes de
 * {@link MercadoPagoWebhookVerifierTest} assinam e verificam com o mesmo algoritmo,
 * então não pegam divergência com o que o MP assina de fato.
 *
 * <p>Só roda com {@code MP_WEBHOOK_CAPTURE} (caminho do JSONL) e
 * {@code MERCADOPAGO_WEBHOOK_SECRET} definidos — nunca no CI.
 */
@EnabledIfEnvironmentVariable(named = "MP_WEBHOOK_CAPTURE", matches = ".+")
class MercadoPagoWebhookRealCaptureTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void notificacoesReais_validamComOSegredoDoPainel() throws Exception {
        var verifier = new MercadoPagoWebhookVerifier(System.getenv("MERCADOPAGO_WEBHOOK_SECRET"));
        int avaliadas = 0;

        for (String linha : Files.readAllLines(Path.of(System.getenv("MP_WEBHOOK_CAPTURE")))) {
            JsonNode captura = mapper.readTree(linha);
            JsonNode headers = captura.path("headers");
            if (!headers.has("x-signature")) continue;

            JsonNode body = mapper.readTree(captura.path("body").asText("{}"));
            // Mesma ordem do MercadoPagoWebhookController: corpo primeiro, query depois.
            String dataId = body.path("data").path("id").asText(null);
            if (dataId == null || dataId.isBlank()) {
                dataId = captura.path("query").path("data.id").asText(null);
            }

            boolean valida = verifier.valido(
                    headers.path("x-signature").asText(),
                    headers.path("x-request-id").asText(null),
                    dataId);
            System.out.printf("%s action=%s data.id=%s -> %s%n",
                    captura.path("received_at").asText(), body.path("action").asText("?"),
                    dataId, valida ? "VALIDA" : "INVALIDA");

            assertThat(valida).as("assinatura da notificação data.id=%s", dataId).isTrue();
            avaliadas++;
        }

        assertThat(avaliadas).as("notificações com x-signature no arquivo").isPositive();
    }
}
