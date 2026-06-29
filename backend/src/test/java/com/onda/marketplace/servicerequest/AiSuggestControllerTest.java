package com.onda.marketplace.servicerequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiSuggestController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class AiSuggestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  AiSuggestionService aiService;

    @Test
    void suggest_iaDisponivel_retornaSourceAI() throws Exception {
        var suggestion = new AiSuggestion("Consertar torneira da cozinha",
                BigDecimal.valueOf(80), BigDecimal.valueOf(200));
        when(aiService.suggest(any(), any())).thenReturn(Optional.of(suggestion));

        mvc.perform(post("/api/v1/services/ai/suggest")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new AiSuggestRequest("torneira pingando", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("AI"))
                .andExpect(jsonPath("$.descricaoSugerida").value("Consertar torneira da cozinha"))
                .andExpect(jsonPath("$.faixaMin").value(80))
                .andExpect(jsonPath("$.faixaMax").value(200));
    }

    @Test
    void suggest_iaIndisponivel_retornaFallbackManual() throws Exception {
        when(aiService.suggest(any(), any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/services/ai/suggest")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new AiSuggestRequest(null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK_MANUAL"))
                .andExpect(jsonPath("$.descricaoSugerida").doesNotExist());
    }
}
