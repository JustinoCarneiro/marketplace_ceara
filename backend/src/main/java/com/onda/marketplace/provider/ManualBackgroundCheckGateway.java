package com.onda.marketplace.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "backgroundCheckGatewayConfig", proxyBeanMethods = false)
class ManualBackgroundCheckGateway {
    @Bean
    @ConditionalOnMissingBean(BackgroundCheckGateway.class)
    BackgroundCheckGateway manualBackgroundCheckGateway() {
        // Sem fornecedor, não transmite dados nem simula aprovação criminal.
        return (checkId, cpf) -> BackgroundCheckGateway.Result.INCONCLUSIVO;
    }
}
