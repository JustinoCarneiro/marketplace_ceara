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
     * ninguém contrata a si mesmo. Excluído na própria consulta, ANTES do {@code LIMIT} (achado da revisão cruzada,
     * 2026-10-05): filtrar depois, com o limite já aplicado, podia voltar vazio havendo outro prestador próximo — se
     * ele fosse o 1º resultado (limite=1), o filtro o removia e não trazia o 2º.
     */
    @Transactional(readOnly = true)
    public List<NearbyProviderDto> findNearby(NearbyQuery query, UUID quemBusca) {
        return profileRepository
                .findNearby(query.lat(), query.lng(), query.raio(), query.categoria(), query.limite(), quemBusca)
                .stream()
                .map(NearbyProviderDto::from)
                .toList();
    }
}
