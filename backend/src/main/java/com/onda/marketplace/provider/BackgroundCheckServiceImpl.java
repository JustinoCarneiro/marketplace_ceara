package com.onda.marketplace.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
class BackgroundCheckServiceImpl implements BackgroundCheckService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundCheckServiceImpl.class);

    @Async
    @Override
    public void scheduleCheck(ProviderProfile profile) {
        // Stub: integração com bureau externo a ser implementada em fase operacional.
        // O prestador começa como EM_VERIFICACAO; webhook externo atualiza para VERIFICADO/REPROVADO.
        log.info("Background check agendado para provider {}", profile.getId());
    }
}
