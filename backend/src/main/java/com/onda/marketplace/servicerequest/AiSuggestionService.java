package com.onda.marketplace.servicerequest;

import java.util.Optional;

public interface AiSuggestionService {

    default Optional<AiSuggestion> suggest(String descricao, String categoria) {
        return suggest(descricao, categoria, "anonymous");
    }

    Optional<AiSuggestion> suggest(String descricao, String categoria, String userKey);
}
