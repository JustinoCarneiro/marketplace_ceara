package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping("/register/provider")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterProviderRequest req, HttpServletRequest httpReq) {
        return providerService.register(req, httpReq.getRemoteAddr());
    }

    /**
     * Conta única com papéis: o cliente logado passa a prestar serviço NA MESMA conta (sem segunda conta, sem novo e-mail).
     * Exige sessão de cliente: a rota não está na lista pública do SecurityConfig.
     */
    @PostMapping("/become-provider")
    @PreAuthorize("hasRole('CLIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse becomeProvider(@Valid @RequestBody BecomeProviderRequest req,
                                       Authentication auth, HttpServletRequest httpReq) {
        return providerService.tornarPrestador(UUID.fromString(auth.getName()), req, httpReq.getRemoteAddr());
    }
}
