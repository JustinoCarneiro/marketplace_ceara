package com.onda.marketplace.discovery;

import java.math.BigDecimal;
import java.util.UUID;

public record NearbyProviderDto(
        UUID       id,
        String     nome,
        String     categoria,
        String     bio,
        String     statusVerificacao,
        BigDecimal notaMedia,
        Double     distanciaMetros
) {
    static NearbyProviderDto from(NearbyProviderView v) {
        return new NearbyProviderDto(
                v.getId(), v.getNome(), v.getCategoria(), v.getBio(),
                v.getStatusVerificacao(), v.getNotaMedia(), v.getDistanciaMetros());
    }
}
