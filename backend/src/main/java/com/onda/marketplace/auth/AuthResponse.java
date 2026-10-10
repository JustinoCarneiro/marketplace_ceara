package com.onda.marketplace.auth;

import java.util.List;
import java.util.UUID;

/**
 * Resposta de login/cadastro/troca de papel. {@code role} é o papel EM USO na sessão (o do token); {@code papeis} são
 * todos os que a conta tem — é o que o app usa para oferecer "alternar para modo prestador/cliente".
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String role,
        UUID   userId,
        String nome,
        String email,
        List<String> papeis
) {}
