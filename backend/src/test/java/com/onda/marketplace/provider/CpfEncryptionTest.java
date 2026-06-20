package com.onda.marketplace.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpfEncryptionTest {

    private final CpfEncryptor encryptor = new CpfEncryptor("01234567890123456789012345678901");

    @Test
    void encrypt_thenDecrypt_returnsSameValue() {
        String original = "123.456.789-09";
        String cipher   = encryptor.encrypt(original);
        String decoded  = encryptor.decrypt(cipher);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encrypted_isNotPlainText() {
        String cipher = encryptor.encrypt("123.456.789-09");
        assertThat(cipher).doesNotContain("123.456.789-09");
    }
}
