package com.onda.marketplace.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByServiceRequestIdOrderByCreatedAtAsc(UUID serviceRequestId);
}
