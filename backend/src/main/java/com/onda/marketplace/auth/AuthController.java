package com.onda.marketplace.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** US01 — Cadastro do Cliente */
    @PostMapping("/register/client")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerClient(@Valid @RequestBody RegisterClientRequest req) {
        return authService.registerClient(req);
    }

    /** US12 — Login */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** US12 — Refresh token */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    /** Antifraude Camada 2 — vincula CPF ao cliente no 1º pagamento (LGPD: só o hash é guardado) */
    @PostMapping("/verify-identity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyIdentity(@Valid @RequestBody VerifyIdentityRequest req,
                               Authentication auth) {
        // JwtAuthFilter define o principal como o id do usuário (subject do JWT),
        // não um UserDetails — mesmo padrão dos demais controllers.
        UUID userId = UUID.fromString(auth.getName());
        authService.verifyIdentity(req.cpf(), userId);
    }
}
