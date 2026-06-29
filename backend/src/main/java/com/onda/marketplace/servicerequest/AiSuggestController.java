package com.onda.marketplace.servicerequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint de sugestão por IA (US14 / M04).
 *
 * <p>O contrato garante <b>fallback manual obrigatório</b>: se o serviço de IA
 * falhar ou estiver indisponível, a resposta vem com {@code source: "FALLBACK_MANUAL"}
 * e campos nulos — o frontend segue com o fluxo manual. IA nunca bloqueia.
 */
@RestController
@RequestMapping("/api/v1/services/ai")
public class AiSuggestController {

    private final AiSuggestionService aiService;

    public AiSuggestController(AiSuggestionService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/suggest")
    public ResponseEntity<AiSuggestResponse> suggest(@RequestBody AiSuggestRequest request) {
        return ResponseEntity.ok(
                aiService.suggest(request.descricao(), null)
                        .map(AiSuggestResponse::fromAi)
                        .orElseGet(AiSuggestResponse::fallback));
    }
}
