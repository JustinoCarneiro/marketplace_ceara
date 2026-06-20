package com.onda.marketplace.servicerequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceMediaRepository extends JpaRepository<ServiceMedia, UUID> {}
