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

    // Lista para o painel admin (US25) — fetch join do usuário evita lazy fora da transação
    @Query("select p from ProviderProfile p join fetch p.user")
    List<ProviderProfile> findAllWithUser();

    @Query("select p from ProviderProfile p join fetch p.user where p.statusVerificacao = :status")
    List<ProviderProfile> findByStatusWithUser(@Param("status") ProviderStatus status);

    // PostGIS ST_DWithin sobre índice GiST — SLA p95 < 300ms (TS03)
    // "id" aqui é o user_id (não o PK de providers_profile): o mobile navega do card da busca
    // direto pra ProviderProfileScreen com esse valor, e GET /providers/{userId} (perfil
    // público) busca por user_id. Selecionar pp.id fazia esse lookup falhar sempre com
    // PROVIDER_NOT_FOUND — nenhum teste E2E cobria "tocar num card da busca", só o fluxo de
    // pedido por categoria, por isso sobreviveu a todas as auditorias anteriores.
    @Query(nativeQuery = true, value = """
            SELECT pp.user_id             AS id,
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
