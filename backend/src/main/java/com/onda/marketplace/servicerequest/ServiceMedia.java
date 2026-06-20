package com.onda.marketplace.servicerequest;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_media")
public class ServiceMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false)
    private ServiceRequest serviceRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType tipo;

    @Column(nullable = false)
    private String url;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ServiceMedia() {}

    public ServiceMedia(ServiceRequest serviceRequest, MediaType tipo, String url) {
        this.serviceRequest = serviceRequest;
        this.tipo           = tipo;
        this.url            = url;
    }

    public UUID getId()                     { return id; }
    public ServiceRequest getServiceRequest() { return serviceRequest; }
    public MediaType getTipo()              { return tipo; }
    public String getUrl()                  { return url; }
    public Instant getCreatedAt()           { return createdAt; }
}
