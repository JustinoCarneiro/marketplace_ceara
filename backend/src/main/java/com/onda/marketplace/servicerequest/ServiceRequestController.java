package com.onda.marketplace.servicerequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {

    private final ServiceRequestService service;

    public ServiceRequestController(ServiceRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceRequestDto create(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateServiceRequestRequest req,
            Authentication auth) {

        UUID clienteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        return service.create(clienteId, req, idempotencyKey);
    }

    @PostMapping("/{id}/media")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse addMedia(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("tipo") String tipo) {
        return service.addMedia(id, file, tipo);
    }
}
