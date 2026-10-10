package com.onda.marketplace.config;

import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.provider.CpfEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CpfEncryptor cpfEncryptor(@Value("${cpf.encryption-key}") String key) {
        return new CpfEncryptor(key);
    }

    /**
     * Chave do hash do CPF própria, distinta da que cifra (uma chave por finalidade). Igual à de cifra seria o desenho
     * antigo: trocar uma invalidaria a outra, e o hash dos clientes não se refaz.
     */
    @Bean
    CpfHashService cpfHashService(@Value("${cpf.hash-key}") String chave,
                                  @Value("${cpf.hash-key-version:2}") int versao,
                                  @Value("${cpf.hash-key-previous:}") String anterior,
                                  @Value("${cpf.encryption-key}") String chaveDeCifra) {
        if (chave.equals(chaveDeCifra)) {
            throw new IllegalStateException(
                    "CPF_HASH_KEY não pode ser igual a CPF_ENCRYPTION_KEY: cada finalidade tem a sua chave.");
        }
        return new CpfHashService(chave, versao, anterior == null || anterior.isBlank() ? null : anterior);
    }
}
