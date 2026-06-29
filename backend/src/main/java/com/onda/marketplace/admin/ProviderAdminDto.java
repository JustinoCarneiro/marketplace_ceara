package com.onda.marketplace.admin;

import com.onda.marketplace.provider.ProviderProfile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de saída da lista de prestadores no painel admin (US25).
 *
 * <p>{@code id} é o <b>userId</b> (não o id do perfil) — é o identificador que as
 * ações de moderação ({@code /providers/{userId}/verify|reject|moderate}) esperam.
 */
public record ProviderAdminDto(
        UUID       id,
        String     nome,
        String     categoria,
        String     statusVerificacao,
        BigDecimal notaMedia
) {
    public static ProviderAdminDto from(ProviderProfile p) {
        return new ProviderAdminDto(
                p.getUser().getId(),
                p.getUser().getNome(),
                p.getCategoria(),
                p.getStatusVerificacao().name(),
                p.getNotaMedia());
    }
}
