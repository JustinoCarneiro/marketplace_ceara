package com.onda.marketplace.sos;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sos")
public class SosController {

    private final SosService sosService;

    public SosController(SosService sosService) {
        this.sosService = sosService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public ResponseEntity<SosAlertDto> acionarSos(
            @RequestBody AcionarSosRequest request,
            Authentication auth) {

        UUID userId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        SosAlertDto dto = sosService.acionarSos(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resolver(@PathVariable UUID id) {
        sosService.resolver(id);
        return ResponseEntity.ok().build();
    }
}
