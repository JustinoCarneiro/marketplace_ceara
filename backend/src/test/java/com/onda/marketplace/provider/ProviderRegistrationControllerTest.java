package com.onda.marketplace.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProviderController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class ProviderRegistrationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  ProviderService providerService;

    @Test
    void registerProvider_validPayload_returns201() throws Exception {
        var req = new RegisterProviderRequest(
                "Carlos Silva", "carlos@example.com", "Senha@123",
                "999.999.999-99", "ELETRICISTA");
        when(providerService.register(any())).thenReturn(
                new AuthResponse("access-token", "refresh-token", "ROLE_PROVIDER"));

        mvc.perform(post("/api/v1/auth/register/provider")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.role").value("ROLE_PROVIDER"));
    }

    @Test
    void registerProvider_missingFields_returns422() throws Exception {
        mvc.perform(post("/api/v1/auth/register/provider")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
