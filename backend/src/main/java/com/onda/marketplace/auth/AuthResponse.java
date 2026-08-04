package com.onda.marketplace.auth;

import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String role,
        UUID   userId,
        String nome,
        String email
) {}
