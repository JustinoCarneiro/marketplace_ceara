package com.onda.marketplace.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByServiceRequestIdAndTipo(UUID serviceRequestId, ReviewType tipo);

    /** As duas avaliações (cliente↔prestador) de um mesmo pedido. */
    List<Review> findByServiceRequestId(UUID serviceRequestId);

    Optional<Review> findByServiceRequestIdAndTipo(UUID serviceRequestId, ReviewType tipo);

    /**
     * Perfil público do prestador no app (mobile) — double-blind: só as reveladas.
     * Usar a variante sem filtro aqui vazaria a nota que ainda está oculta.
     */
    List<Review> findByAvaliadoIdAndTipoAndReveladaTrueOrderByCriadoEmDesc(
            UUID avaliadoId, ReviewType tipo);

    /** Média da reputação — conta apenas avaliações já reveladas (ver double-blind). */
    @Query("""
           SELECT COALESCE(AVG(r.nota), 0.0) FROM Review r
            WHERE r.avaliadoId = :prestadorId
              AND r.tipo = :tipo
              AND r.revelada = true
           """)
    double calcularMediaNota(@Param("prestadorId") UUID prestadorId,
                             @Param("tipo") ReviewType tipo);

    /** Avaliações ocultas cujo prazo de reciprocidade já venceu (job de revelação). */
    List<Review> findByReveladaFalseAndCriadoEmBefore(Instant limite);
}
