package com.onda.marketplace.denuncia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DenunciaRepository extends JpaRepository<Denuncia, UUID> {
    List<Denuncia> findByStatusOrderByCriadoEmDesc(StatusDenuncia status);
}
