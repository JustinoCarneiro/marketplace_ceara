package com.onda.marketplace.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Encerra todas as sessões do usuário (troca de senha, US35): ninguém continua logado com a senha antiga. */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.user.id = :userId AND r.revogado = false")
    int revogarTodosDoUsuario(@Param("userId") UUID userId);
}
