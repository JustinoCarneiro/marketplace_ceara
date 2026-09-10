package com.onda.marketplace.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderProfileController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class ProviderProfileControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ProviderService providerService;

    private static final String USER_ID = UUID.randomUUID().toString();

    @Test
    void definirChavePix_chamaOServico_eRetornaCadastrada() throws Exception {
        mvc.perform(put("/api/v1/providers/me/chave-pix").with(csrf()).with(user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chavePix\":\"prestador@pix.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadastrada").value(true));

        verify(providerService).atualizarChavePix(eq(UUID.fromString(USER_ID)), eq("prestador@pix.com"));
    }

    @Test
    void definirChavePix_vazia_retorna422() throws Exception {
        mvc.perform(put("/api/v1/providers/me/chave-pix").with(csrf()).with(user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chavePix\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void statusChavePix_refleteOServico() throws Exception {
        when(providerService.chavePixCadastrada(any())).thenReturn(true);

        mvc.perform(get("/api/v1/providers/me/chave-pix").with(user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cadastrada").value(true));
    }
}
