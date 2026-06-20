package com.onda.marketplace.provider;

import com.onda.marketplace.discovery.NearbyProviderView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {

    Optional<ProviderProfile> findByUserId(UUID userId);

    // Métricas/alertas do painel admin (US23/US30)
    long countByStatusVerificacao(ProviderStatus statusVerificacao);

    // PostGIS ST_DWithin sobre índice GiST — SLA p95 < 300ms (TS03)
    @Query(nativeQuery = true, value = """
            SELECT pp.id,
                   u.nome,
                   pp.categoria,
                   pp.bio,
                   pp.status_verificacao    AS statusVerificacao,
                   pp.nota_media            AS notaMedia,
                   ST_Distance(pp.localizacao,
                       ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distanciaMetros
            FROM providers_profile pp
            JOIN users u ON u.id = pp.user_id
            WHERE u.ativo = TRUE
              AND pp.status_verificacao = 'VERIFICADO'
              AND pp.localizacao IS NOT NULL
              AND (:categoria IS NULL OR pp.categoria = :categoria)
              AND ST_DWithin(pp.localizacao,
                      ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                      :raioMetros)
            ORDER BY distanciaMetros
            LIMIT :limite
            """)
    List<NearbyProviderView> findNearby(
            @Param("lat")        double lat,
            @Param("lng")        double lng,
            @Param("raioMetros") double raioMetros,
            @Param("categoria")  String categoria,
            @Param("limite")     int    limite);
}
