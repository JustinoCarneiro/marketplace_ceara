package com.onda.marketplace.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
}
