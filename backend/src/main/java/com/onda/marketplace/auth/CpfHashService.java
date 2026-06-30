package com.onda.marketplace.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Produz hash determinístico (HMAC-SHA256) do CPF para indexação de unicidade.
 * LGPD: somente o hash entra no banco — o CPF em claro nunca é persistido para clientes.
 * O mesmo CPF sempre gera o mesmo hash com a mesma chave, permitindo constraint UNIQUE.
 */
public class CpfHashService {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] keyBytes;

    public CpfHashService(String hmacKey) {
        this.keyBytes = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String cpf) {
        String normalized = cpf.replaceAll("[^0-9]", "");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, ALGORITHM));
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular hash do CPF", e);
        }
    }
}
