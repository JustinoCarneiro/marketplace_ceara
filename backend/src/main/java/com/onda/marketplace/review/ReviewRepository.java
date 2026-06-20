package com.onda.marketplace.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByServiceRequestIdAndTipo(UUID serviceRequestId, ReviewType tipo);

    @Query("SELECT COALESCE(AVG(r.nota), 0.0) FROM Review r WHERE r.avaliadoId = :prestadorId AND r.tipo = :tipo")
    double calcularMediaNota(@Param("prestadorId") UUID prestadorId, @Param("tipo") ReviewType tipo);
}
