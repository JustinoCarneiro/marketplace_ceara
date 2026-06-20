package com.onda.marketplace.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String role
) {}
