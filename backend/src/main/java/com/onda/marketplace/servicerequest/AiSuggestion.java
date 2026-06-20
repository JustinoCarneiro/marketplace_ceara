package com.onda.marketplace.servicerequest;

import java.math.BigDecimal;

public record AiSuggestion(String descricaoSugerida, BigDecimal faixaMin, BigDecimal faixaMax) {}
