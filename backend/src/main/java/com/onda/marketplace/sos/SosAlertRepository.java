package com.onda.marketplace.sos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {
    List<SosAlert> findByStatusOrderByCriadoEmDesc(SosAlertStatus status);
}
