package com.onda.marketplace.denuncia;

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

@WebMvcTest(DenunciaController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class DenunciaControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  DenunciaService denunciaService;

    @Test
    void criar_payloadValido_retorna201() throws Exception {
        var dto = new DenunciaDto(UUID.randomUUID(), "PRESTADOR", "ABERTA", Instant.now());
        when(denunciaService.criar(any(), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/denuncias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new CreateDenunciaRequest(TipoDenuncia.PRESTADOR, UUID.randomUUID(), "Perfil falso", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTA"));
    }

    @Test
    void criar_motivoEmBranco_retorna422() throws Exception {
        mvc.perform(post("/api/v1/denuncias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"PRESTADOR\",\"alvoId\":\"" + UUID.randomUUID() + "\",\"motivo\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void criar_semTipo_retorna422() throws Exception {
        mvc.perform(post("/api/v1/denuncias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alvoId\":\"" + UUID.randomUUID() + "\",\"motivo\":\"Perfil falso\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
