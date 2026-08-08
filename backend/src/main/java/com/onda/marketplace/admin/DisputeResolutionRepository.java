package com.onda.marketplace.admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeResolutionRepository extends JpaRepository<DisputeResolution, UUID> {
    // Detalhe de disputa — UNIQUE(service_request_id) garante no máximo 1 resultado
    java.util.Optional<DisputeResolution> findByServiceRequestId(UUID serviceRequestId);

    /**
     * Tempo médio, em horas, entre abrir e mediar a disputa (US23).
     *
     * <p>Não existe coluna com "quando a disputa foi aberta": o marco é o alerta
     * {@code DISPUTA} em admin_notifications, criado por {@code openDispute()} no
     * mesmo instante. Disputa sem alerta correspondente (dado semeado, por exemplo)
     * fica de fora da média — melhor omitir do que inventar duração.
     *
     * <p>Nativa: {@code EXTRACT(EPOCH FROM interval)} é específico do Postgres, que é
     * o banco de produção (PostGIS já é requisito do projeto).
     */
    @org.springframework.data.jpa.repository.Query(value = """
           SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (dr.criado_em - an.criado_em)) / 3600.0), 0)
             FROM dispute_resolutions dr
             JOIN admin_notifications an
               ON an.ref_id = dr.service_request_id AND an.tipo = 'DISPUTA'
            WHERE dr.criado_em >= :de AND dr.criado_em < :ate
           """, nativeQuery = true)
    double tempoMedioResolucaoHoras(
            @org.springframework.data.repository.query.Param("de") java.time.Instant de,
            @org.springframework.data.repository.query.Param("ate") java.time.Instant ate);
}
