package com.onda.marketplace.provider;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface BackgroundCheckRepository extends JpaRepository<BackgroundCheck, UUID> {
    @Query("select b.id from BackgroundCheck b where b.status in ('PENDENTE', 'PROCESSANDO') and b.availableAt <= :now order by b.requestedAt")
    List<UUID> pending(Instant now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BackgroundCheck b where b.id = :id")
    Optional<BackgroundCheck> locked(UUID id);
}
