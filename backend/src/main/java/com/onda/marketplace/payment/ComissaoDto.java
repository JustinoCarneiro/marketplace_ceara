package com.onda.marketplace.payment;

import java.math.BigDecimal;

/** Comissão vigente da plataforma; {@code percentualComissao} é fração (0.10 = 10%). */
public record ComissaoDto(BigDecimal percentualComissao) {}
