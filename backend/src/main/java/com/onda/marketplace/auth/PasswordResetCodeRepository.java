package com.onda.marketplace.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    /**
     * Códigos abertos e dentro da validade, o mais novo primeiro, com trava de escrita: duas
     * confirmações simultâneas do mesmo código (ou rajadas de palpites) se enfileiram em vez de
     * ler o mesmo contador de tentativas e furar o limite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT c FROM PasswordResetCode c
            WHERE c.userId = :userId AND c.closedAt IS NULL AND c.expiresAt > :agora
            ORDER BY c.createdAt DESC
           """)
    List<PasswordResetCode> ativosDoUsuarioComTrava(@Param("userId") UUID userId,
                                                    @Param("agora") Instant agora);

    /** Um pedido novo invalida os anteriores: só o último código enviado funciona. */
    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.closedAt = :agora WHERE c.userId = :userId AND c.closedAt IS NULL")
    int fecharAtivosDoUsuario(@Param("userId") UUID userId, @Param("agora") Instant agora);

    /** Códigos emitidos desde {@code desde}, abertos ou não — base do limite de pedidos por hora. */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant desde);
}
