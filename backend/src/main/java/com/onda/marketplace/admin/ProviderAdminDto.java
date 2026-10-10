package com.onda.marketplace.admin;

import com.onda.marketplace.provider.ProviderProfile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de saída da lista de prestadores no painel admin (US25).
 *
 * <p>{@code id} é o <b>userId</b> (não o id do perfil) — é o identificador que as
 * ações de moderação ({@code /providers/{userId}/verify|reject|moderate}) esperam.
 *
 * <p>{@code cpfConciliado = false}: o mesmo CPF estava em outra conta quando o sistema passou a conferir (duplicata legada); o
 * prestador não opera nem aparece na busca até o suporte decidir com a ação {@code CONCILIAR_CPF}. É este campo que acende o aviso e o
 * botão do painel — sem ele na lista a tela não tem como saber quem precisa de decisão.
 */
public record ProviderAdminDto(
        UUID       id,
        String     nome,
        String     categoria,
        String     statusVerificacao,
        BigDecimal notaMedia,
        boolean    cpfConciliado
) {
    public static ProviderAdminDto from(ProviderProfile p) {
        return new ProviderAdminDto(
                p.getUser().getId(),
                p.getUser().getNome(),
                p.getCategoria(),
                p.getStatusVerificacao().name(),
                p.getNotaMedia(),
                p.isCpfConciliado());
    }
}
