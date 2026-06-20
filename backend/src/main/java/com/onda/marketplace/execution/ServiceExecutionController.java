package com.onda.marketplace.execution;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests/{id}")
public class ServiceExecutionController {

    private final ServiceExecutionService executionService;

    public ServiceExecutionController(ServiceExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<Void> start(@PathVariable UUID id, Authentication auth) {
        UUID prestadorId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        executionService.start(id, prestadorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirm-completion")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> confirmCompletion(@PathVariable UUID id, Authentication auth) {
        UUID clienteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        executionService.confirmCompletion(id, clienteId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dispute")
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public ResponseEntity<Void> openDispute(@PathVariable UUID id) {
        executionService.openDispute(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        executionService.cancel(id);
        return ResponseEntity.ok().build();
    }
}
