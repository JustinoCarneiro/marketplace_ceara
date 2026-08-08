package com.onda.marketplace.sos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {
    List<SosAlert> findByStatusOrderByCriadoEmDesc(SosAlertStatus status);

    // Alerta operacional do painel admin (US30)
    long countByStatus(SosAlertStatus status);

    // Métrica de fluxo do dashboard (US23): acionamentos dentro do período
    @org.springframework.data.jpa.repository.Query("""
           SELECT COUNT(s) FROM SosAlert s
            WHERE s.criadoEm >= :de AND s.criadoEm < :ate
           """)
    long contarNoPeriodo(@org.springframework.data.repository.query.Param("de") java.time.Instant de,
                         @org.springframework.data.repository.query.Param("ate") java.time.Instant ate);
}
