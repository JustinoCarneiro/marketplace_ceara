package com.onda.marketplace.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, UUID> {}
