package com.onda.marketplace.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@Import(TestSecurityConfig.class)
class ReviewControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  ReviewService reviewService;

    private static final UUID SR_ID = UUID.randomUUID();

    @Test
    void review_notaValida_returns201() throws Exception {
        var dto = new ReviewDto(UUID.randomUUID(), 5, "Excelente!", "CLIENTE_AVALIA_PRESTADOR", Instant.now());
        when(reviewService.avaliar(any(), any(), any(), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/service-requests/{id}/review", SR_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AvaliarRequest(5, "Excelente!"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(5))
                .andExpect(jsonPath("$.tipo").value("CLIENTE_AVALIA_PRESTADOR"));
    }

    @Test
    void review_notaInvalida_returns422() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/review", SR_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new AvaliarRequest(6, "nota acima do limite"))))
                .andExpect(status().isUnprocessableEntity());
    }
}
