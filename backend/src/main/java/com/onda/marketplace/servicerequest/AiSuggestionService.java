package com.onda.marketplace.servicerequest;

import java.util.Optional;

public interface AiSuggestionService {
    Optional<AiSuggestion> suggest(String descricao, String categoria);
}
