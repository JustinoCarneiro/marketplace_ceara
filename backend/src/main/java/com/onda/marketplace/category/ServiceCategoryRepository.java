package com.onda.marketplace.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório do catálogo de categorias (US28).
 */
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {

    boolean existsBySlug(String slug);

    List<ServiceCategory> findAllByOrderByNomeAsc();
}
