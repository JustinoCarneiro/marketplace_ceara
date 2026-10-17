package com.onda.marketplace.provider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class BackgroundCheckServiceImpl implements BackgroundCheckService {
    private final BackgroundCheckRepository checks;
    BackgroundCheckServiceImpl(BackgroundCheckRepository checks) { this.checks = checks; }

    /** A fila nasce na MESMA transação do cadastro: rollback não deixa trabalho órfão. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleCheck(ProviderProfile profile) {
        checks.save(new BackgroundCheck(profile.getId()));
    }
}
