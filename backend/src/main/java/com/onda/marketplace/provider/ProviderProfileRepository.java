package com.onda.marketplace.provider;

import com.onda.marketplace.discovery.NearbyProviderView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {

    Optional<ProviderProfile> findByUserId(UUID userId);

    /**
     * Prestadores com CPF cifrado cujo hash do CPF falta (cadastrados antes de o cadastro gravá-lo) ou está numa versão de
     * chave anterior à atual (rotação): o CPF decifrado deixa regravar o hash.
     */
    @Query("""
           SELECT p.user.id AS userId, p.cpfCifrado AS cpfCifrado FROM ProviderProfile p
            WHERE (p.user.cpfHash IS NULL OR p.user.cpfHashVersao < :versaoAtual)
              AND p.user.excluidoEm IS NULL AND p.cpfCifrado IS NOT NULL
           """)
    List<ProviderCpfBackfill.PerfilSemHash> semHashDoCpf(@Param("versaoAtual") int versaoAtual);

    /**
     * Prestadores cujo hash já está gravado EXATAMENTE nesta versão, com CPF cifrado disponível — a âncora que
     * {@code CpfHashKeyCheck} usa para cruzar a chave dessa versão (atual ou anterior) contra um CPF de verdade,
     * na 1ª subida em que ainda não há um verificador gravado para ela (achado da revisão cruzada, 2026-10-05).
     */
    @Query("""
           SELECT p.user.id AS userId, p.cpfCifrado AS cpfCifrado FROM ProviderProfile p
            WHERE p.user.cpfHash IS NOT NULL AND p.user.cpfHashVersao = :versao AND p.cpfCifrado IS NOT NULL
           """)
    List<ProviderCpfBackfill.PerfilSemHash> comHashNaVersao(@Param("versao") int versao);

    /**
     * Nota média por UPDATE direto, sem carregar o perfil (revisão cruzada, 2ª rodada): a avaliação é de OUTRO usuário e
     * rodava {@code findByUserId} + {@code save}, que regrava a linha inteira. Se o prestador excluísse a conta nesse
     * intervalo, o save desfazia a anonimização (bio, CPF cifrado, chave Pix e status voltavam). O UPDATE só toca a nota.
     * {@code agora}: o UPDATE em lote não roda o {@code @PreUpdate}.
     */
    @Modifying
    @Query("UPDATE ProviderProfile p SET p.notaMedia = :media, p.updatedAt = :agora WHERE p.user.id = :userId")
    int atualizarNotaMedia(@Param("userId") UUID userId, @Param("media") BigDecimal media, @Param("agora") Instant agora);

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
    //
    // Achado da revisão cruzada (2026-10-05): o próprio usuário era excluído DEPOIS desta consulta
    // (DiscoveryService), já com o LIMIT aplicado — com limite=1, se ele fosse o 1º resultado, o filtro
    // de depois o removia e não trazia o 2º: a busca voltava vazia havendo outro prestador próximo.
    // Excluído AQUI, antes do LIMIT, o lugar dele na lista vai para quem vem depois.
    //
    // 2ª rodada: perfil com cpf_conciliado = false (duplicata legada de CPF) não aparece: o guard de verificação já o impede de
    // propor/aceitar, e listá-lo só levaria o cliente a um beco (vê um prestador VERIFICADO e recebe PROVIDER_NOT_VERIFIED).
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
              AND pp.user_id <> :quemBusca
              AND pp.cpf_conciliado = TRUE
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
            @Param("limite")     int    limite,
            @Param("quemBusca")  UUID   quemBusca);
}
