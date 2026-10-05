package com.onda.marketplace.admin;

import com.onda.marketplace.auth.User;

import java.util.UUID;

/**
 * DTO de saída da gestão de usuários do painel admin (US26).
 * {@code status} é derivado do estado da conta: "ATIVO" | "SUSPENSO" (flag {@code ativo}) | "EXCLUIDO"
 * (o próprio usuário excluiu a conta, US36 — o nome já vem anonimizado).
 * {@code role} é o papel principal (o do cadastro); {@code papeis}, todos os que a conta tem (conta única, V24).
 * Nunca expõe hash de senha nem CPF (TS04/LGPD).
 */
public record UserAdminDto(
        UUID   id,
        String nome,
        String email,
        String role,
        java.util.List<String> papeis,
        String status
) {
    public static UserAdminDto from(User u) {
        return new UserAdminDto(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole().name(),
                u.getPapeis().stream().sorted().map(Enum::name).toList(),
                status(u));
    }

    private static String status(User u) {
        if (u.isExcluido()) return "EXCLUIDO";
        return u.isAtivo() ? "ATIVO" : "SUSPENSO";
    }
}
