package com.onda.marketplace.discovery;

import java.math.BigDecimal;
import java.util.UUID;

public interface NearbyProviderView {
    UUID          getId();
    String        getNome();
    String        getCategoria();
    String        getBio();
    String        getStatusVerificacao();
    BigDecimal    getNotaMedia();
    Double        getDistanciaMetros();
}
