package com.onda.marketplace.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface StorageDeletionRepository extends JpaRepository<StorageDeletion, UUID> {
    List<StorageDeletion> findTop50ByOrderByCreatedAtAsc();
}
