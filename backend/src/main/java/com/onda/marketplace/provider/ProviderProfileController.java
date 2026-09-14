package com.onda.marketplace.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Autoatendimento do prestador sobre o próprio perfil. Hoje só a chave Pix usada
 * no repasse da conclusão (Modelo A, MKT-49) — a chave é gravada cifrada e nunca
 * devolvida em claro; o GET só informa se já existe.
 */
@RestController
@RequestMapping("/api/v1/providers/me")
public class ProviderProfileController {

    private final ProviderService providerService;

    public ProviderProfileController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PutMapping("/chave-pix")
    @PreAuthorize("hasRole('PROVIDER')")
    public ChavePixStatus definirChavePix(@Valid @RequestBody ChavePixRequest req, Authentication auth) {
        providerService.atualizarChavePix(userId(auth), req.chavePix());
        return new ChavePixStatus(true);
    }

    @GetMapping("/chave-pix")
    @PreAuthorize("hasRole('PROVIDER')")
    public ChavePixStatus statusChavePix(Authentication auth) {
        return new ChavePixStatus(providerService.chavePixCadastrada(userId(auth)));
    }

    private static UUID userId(Authentication auth) {
        return auth != null ? UUID.fromString(auth.getName()) : UUID.randomUUID();
    }

    public record ChavePixRequest(@NotBlank @Size(max = 140) String chavePix) {}

    public record ChavePixStatus(boolean cadastrada) {}
}
