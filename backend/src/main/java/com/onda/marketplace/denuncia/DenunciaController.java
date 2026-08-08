package com.onda.marketplace.denuncia;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/denuncias")
public class DenunciaController {

    private final DenunciaService denunciaService;

    public DenunciaController(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public ResponseEntity<DenunciaDto> criar(
            @Valid @RequestBody CreateDenunciaRequest request,
            Authentication auth) {

        UUID denuncianteId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
        DenunciaDto dto = denunciaService.criar(denuncianteId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
