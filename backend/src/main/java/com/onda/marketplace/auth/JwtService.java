package com.onda.marketplace.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTokenMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms:900000}") long accessTokenMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenMs = accessTokenMs;
    }

    /** {@code contexto}: o papel em uso nesta sessão (a conta pode ter mais de um) — é o que os controllers autorizam. */
    public String generateAccessToken(User user, UserRole contexto) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", contexto.name())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenMs))
                .signWith(key)
                .compact();
    }

    /** Retorna as claims ou lança JwtException (token inválido/expirado). */
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(validateAndExtract(token).getSubject());
    }

    public String extractRole(String token) {
        return validateAndExtract(token).get("role", String.class);
    }
}
