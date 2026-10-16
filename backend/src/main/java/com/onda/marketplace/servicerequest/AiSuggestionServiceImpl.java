package com.onda.marketplace.servicerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@ConditionalOnProperty(name = "marketplace.ai.enabled", havingValue = "false", matchIfMissing = true)
class AiSuggestionServiceImpl implements AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionServiceImpl.class);

    @Override
    public Optional<AiSuggestion> suggest(String descricao, String categoria) {
        return suggest(descricao, categoria, "anonymous");
    }

    @Override
    public Optional<AiSuggestion> suggest(String descricao, String categoria, String userKey) {
        // Stub: integração com modelo de IA a ser implementada.
        // Retorna empty → fallback manual ativado no cliente (princípio: IA nunca bloqueia).
        log.debug("IA stub chamada para categoria={}, userKey={}", categoria, userKey);
        return Optional.empty();
    }
}
