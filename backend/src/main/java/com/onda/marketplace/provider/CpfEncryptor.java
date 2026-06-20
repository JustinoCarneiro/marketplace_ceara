package com.onda.marketplace.provider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class CpfEncryptor {

    private static final String ALGORITHM  = "AES/GCM/NoPadding";
    private static final int    IV_LEN     = 12;
    private static final int    TAG_BITS   = 128;

    private final SecretKeySpec secretKey;

    public CpfEncryptor(String keyString) {
        byte[] raw = keyString.getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(Arrays.copyOf(raw, 32), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar CPF", e);
        }
    }

    public String decrypt(String cipherBase64) {
        try {
            byte[] input = Base64.getDecoder().decode(cipherBase64);
            byte[] iv = Arrays.copyOf(input, IV_LEN);
            byte[] ct = Arrays.copyOfRange(input, IV_LEN, input.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao decifrar CPF", e);
        }
    }
}
