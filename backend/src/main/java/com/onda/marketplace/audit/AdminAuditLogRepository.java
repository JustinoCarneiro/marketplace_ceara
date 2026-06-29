package com.onda.marketplace.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repositório da trilha de auditoria (US22). Append-only — sem operações de
 * atualização/remoção expostas além do que o {@link JpaRepository} oferece
 * (que não são usadas pela aplicação).
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    List<AdminAuditLog> findAllByOrderByCriadoEmDesc();
}
