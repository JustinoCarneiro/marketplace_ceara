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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
                "999.999.999-99", "ELETRICISTA", "Eletricista há 10 anos.", true);
        when(providerService.register(any(), any())).thenReturn(
                new AuthResponse("access-token", "refresh-token", "ROLE_PROVIDER",
                        java.util.UUID.randomUUID(), "Carlos Silva", "carlos@example.com",
                        java.util.List.of("ROLE_CLIENT", "ROLE_PROVIDER")));

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

    // ── Conta única com papéis: o cliente logado passa a prestar serviço na mesma conta

    @Test
    void becomeProvider_validPayload_returns201_usandoOIdDoToken() throws Exception {
        java.util.UUID doToken = java.util.UUID.randomUUID();
        when(providerService.tornarPrestador(eq(doToken), any(), any())).thenReturn(
                new AuthResponse("access-token", "refresh-token", "ROLE_PROVIDER",
                        doToken, "Ana", "ana@example.com", java.util.List.of("ROLE_CLIENT", "ROLE_PROVIDER")));

        mvc.perform(post("/api/v1/auth/become-provider")
                        .with(csrf()).with(user(doToken.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"111.444.777-35\",\"categoria\":\"ELETRICISTA\",\"bio\":\"Faço instalação\",\"aceitouTermos\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_PROVIDER"))
                .andExpect(jsonPath("$.papeis.length()").value(2));

        verify(providerService).tornarPrestador(eq(doToken), any(), any());
    }

    @Test
    void becomeProvider_semAceitarOsTermos_returns422() throws Exception {
        mvc.perform(post("/api/v1/auth/become-provider")
                        .with(csrf()).with(user(java.util.UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"111.444.777-35\",\"categoria\":\"ELETRICISTA\",\"aceitouTermos\":false}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void becomeProvider_semCpf_returns422() throws Exception {
        mvc.perform(post("/api/v1/auth/become-provider")
                        .with(csrf()).with(user(java.util.UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"ELETRICISTA\",\"aceitouTermos\":true}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
