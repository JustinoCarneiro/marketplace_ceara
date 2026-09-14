package com.onda.marketplace.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoWebhookVerifierTest {

    private static final String SECRET  = "webhook-secret-de-teste";
    private static final String REQ_ID  = "b3f1c0de-0000-1111-2222-333344445555";
    private static final String DATA_ID = "123456789";
    private static final String TS      = "1704908010";

    private final MercadoPagoWebhookVerifier verifier = new MercadoPagoWebhookVerifier(SECRET);

    private static String assinar(String secret, String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String header(String ts, String v1) {
        return "ts=" + ts + ",v1=" + v1;
    }

    @Test
    void assinaturaCorreta_comRequestId_valida() {
        String v1 = assinar(SECRET, "id:" + DATA_ID + ";request-id:" + REQ_ID + ";ts:" + TS + ";");
        assertThat(verifier.valido(header(TS, v1), REQ_ID, DATA_ID)).isTrue();
    }

    @Test
    void assinaturaCorreta_semRequestId_valida() {
        String v1 = assinar(SECRET, "id:" + DATA_ID + ";ts:" + TS + ";");
        assertThat(verifier.valido(header(TS, v1), null, DATA_ID)).isTrue();
    }

    @Test
    void v1Adulterado_naoValida() {
        String v1 = assinar(SECRET, "id:" + DATA_ID + ";request-id:" + REQ_ID + ";ts:" + TS + ";");
        String adulterado = "0" + v1.substring(1);
        assertThat(verifier.valido(header(TS, adulterado), REQ_ID, DATA_ID)).isFalse();
    }

    @Test
    void tsDiferenteDoAssinado_naoValida() {
        String v1 = assinar(SECRET, "id:" + DATA_ID + ";request-id:" + REQ_ID + ";ts:" + TS + ";");
        assertThat(verifier.valido(header("1704908099", v1), REQ_ID, DATA_ID)).isFalse();
    }

    @Test
    void segredoErrado_naoValida() {
        String v1 = assinar("outro-segredo", "id:" + DATA_ID + ";request-id:" + REQ_ID + ";ts:" + TS + ";");
        assertThat(verifier.valido(header(TS, v1), REQ_ID, DATA_ID)).isFalse();
    }

    @Test
    void headerAusenteOuMalformado_naoValida() {
        assertThat(verifier.valido(null, REQ_ID, DATA_ID)).isFalse();
        assertThat(verifier.valido("ts=" + TS, REQ_ID, DATA_ID)).isFalse();      // sem v1
        assertThat(verifier.valido("v1=deadbeef", REQ_ID, DATA_ID)).isFalse();   // sem ts
        assertThat(verifier.valido("ts=x,v1=nao-hex", REQ_ID, DATA_ID)).isFalse();
    }

    @Test
    void semSegredoConfigurado_nuncaValida() {
        var semSegredo = new MercadoPagoWebhookVerifier("");
        assertThat(semSegredo.valido(header(TS, "abcdef0123456789"), null, DATA_ID)).isFalse();
    }

    @Test
    void semDataId_naoValida() {
        String v1 = assinar(SECRET, "id:;ts:" + TS + ";");
        assertThat(verifier.valido(header(TS, v1), REQ_ID, null)).isFalse();
        assertThat(verifier.valido(header(TS, v1), REQ_ID, "  ")).isFalse();
    }
}
