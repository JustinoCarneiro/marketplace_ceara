package com.onda.marketplace.provider;

public interface BackgroundCheckService {
    void scheduleCheck(ProviderProfile profile);
}
