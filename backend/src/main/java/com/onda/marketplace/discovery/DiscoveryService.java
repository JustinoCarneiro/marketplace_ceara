package com.onda.marketplace.discovery;

import com.onda.marketplace.provider.ProviderProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DiscoveryService {

    private final ProviderProfileRepository profileRepository;

    public DiscoveryService(ProviderProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public List<NearbyProviderDto> findNearby(NearbyQuery query) {
        return profileRepository
                .findNearby(query.lat(), query.lng(), query.raio(), query.categoria(), query.limite())
                .stream()
                .map(NearbyProviderDto::from)
                .toList();
    }
}
