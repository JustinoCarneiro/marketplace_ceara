package com.onda.marketplace.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Consome o refresh token de forma atômica (revisão cruzada, 2ª rodada): o {@code WHERE revogado = false} faz só UMA de
     * duas chamadas simultâneas com o mesmo token afetar a linha. A leitura + {@code revoke()} + {@code save()} anteriores deixavam as
     * duas passarem em {@code isValid()} antes de qualquer uma revogar, e cada uma emitia uma sessão de 30 dias a partir de um token só.
     *
     * @return 1 se este chamador consumiu o token; 0 se outro já o havia consumido
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.id = :id AND r.revogado = false")
    int revogarSeAindaValido(@Param("id") UUID id);

    /** Encerra todas as sessões do usuário (troca de senha, US35): ninguém continua logado com a senha antiga. */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revogado = true WHERE r.user.id = :userId AND r.revogado = false")
    int revogarTodosDoUsuario(@Param("userId") UUID userId);
}
