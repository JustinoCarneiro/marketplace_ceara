package com.onda.marketplace.servicerequest;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Intenção de exclusão gravada na mesma transação que remove a mídia do pedido. */
@Entity
@Table(name = "storage_deletions")
class StorageDeletion {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 255)
    private String reference;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected StorageDeletion() {}
    StorageDeletion(String reference) { this.reference = reference; }
    String getReference() { return reference; }
}
