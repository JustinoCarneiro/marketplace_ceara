package com.onda.marketplace.servicerequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
class AiSuggestionServiceImpl implements AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionServiceImpl.class);

    @Override
    public Optional<AiSuggestion> suggest(String descricao, String categoria) {
        // Stub: integração com modelo de IA a ser implementada.
        // Retorna empty → fallback manual ativado no cliente (princípio: IA nunca bloqueia).
        log.debug("IA stub chamada para categoria={}", categoria);
        return Optional.empty();
    }
}
