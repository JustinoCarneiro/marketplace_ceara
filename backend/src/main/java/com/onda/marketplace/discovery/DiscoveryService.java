package com.onda.marketplace.discovery;

import com.onda.marketplace.provider.ProviderProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DiscoveryService {

    private final ProviderProfileRepository profileRepository;

    public DiscoveryService(ProviderProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * {@code quemBusca}: o próprio usuário não aparece na busca — a conta é uma só e pode ser cliente e prestador, e
     * ninguém contrata a si mesmo. (O filtro é depois da consulta: a lista pode vir com um a menos que o limite.)
     */
    @Transactional(readOnly = true)
    public List<NearbyProviderDto> findNearby(NearbyQuery query, UUID quemBusca) {
        return profileRepository
                .findNearby(query.lat(), query.lng(), query.raio(), query.categoria(), query.limite())
                .stream()
                .map(NearbyProviderDto::from)
                .filter(p -> !quemBusca.equals(p.id()))
                .toList();
    }
}
