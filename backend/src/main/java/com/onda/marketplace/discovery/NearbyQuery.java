package com.onda.marketplace.discovery;

public record NearbyQuery(
        double  lat,
        double  lng,
        double  raio,
        String  categoria,
        int     limite
) {}
