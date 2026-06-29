package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;

import java.util.UUID;

/**
 * DTO de saída da gestão de usuários do painel admin (US26).
 * {@code status} é derivado do flag {@code ativo}: "ATIVO" | "SUSPENSO".
 * Nunca expõe hash de senha nem CPF (TS04/LGPD).
 */
public record UserAdminDto(
        UUID   id,
        String nome,
        String email,
        String role,
        String status
) {
    public static UserAdminDto from(User u) {
        return new UserAdminDto(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole().name(),
                u.isAtivo() ? "ATIVO" : "SUSPENSO");
    }
}
