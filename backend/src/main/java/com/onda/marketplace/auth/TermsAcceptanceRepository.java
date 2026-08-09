package com.onda.marketplace.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, UUID> {
    List<TermsAcceptance> findByUserId(UUID userId);
}
