package com.onda.marketplace.review;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-requests/{id}/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT','PROVIDER')")
    public ResponseEntity<ReviewDto> avaliar(
            @PathVariable UUID id,
            @Valid @RequestBody AvaliarRequest request,
            Authentication auth) {

        UUID avaliadorId = auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();

        // Tipo determinado pelo role: CLIENT avalia o prestador; PROVIDER avalia o cliente
        boolean isProvider = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PROVIDER"));
        ReviewType tipo = isProvider
                ? ReviewType.PRESTADOR_AVALIA_CLIENTE
                : ReviewType.CLIENTE_AVALIA_PRESTADOR;

        ReviewDto dto = reviewService.avaliar(id, avaliadorId, tipo, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
