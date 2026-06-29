package com.onda.marketplace.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Repositório de alertas do painel admin (M12/US30).
 */
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, UUID> {

    /** Lista todas as notificações, filtradas por estado de leitura. */
    List<AdminNotification> findByLidaOrderByCriadoEmDesc(boolean lida);

    /** Lista todas (sem filtro) — usada quando {@code lida} é null no controller. */
    List<AdminNotification> findAllByOrderByCriadoEmDesc();

    /** Contagem de não lidas para o badge do painel. */
    long countByLidaFalse();

    /** Marca todas as não lidas como lidas (US30); retorna quantas foram afetadas. */
    @Modifying
    @Query("update AdminNotification n set n.lida = true where n.lida = false")
    int marcarTodasLidas();
}
