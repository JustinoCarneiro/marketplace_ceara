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

    @Bean
    CpfHashService cpfHashService(@Value("${cpf.encryption-key}") String key) {
        return new CpfHashService(key);
    }
}
