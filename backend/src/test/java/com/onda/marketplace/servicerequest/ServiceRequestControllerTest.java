package com.onda.marketplace.servicerequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ServiceRequestController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class ServiceRequestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean  ServiceRequestService service;

    private static final String IDEM_KEY = UUID.randomUUID().toString();

    @Test
    void createRequest_validPayload_returns201() throws Exception {
        var dto = new ServiceRequestDto(UUID.randomUUID(), "ELETRICISTA", "Chuveiro sem funcionar",
                "PENDENTE", "Instalação de chuveiro elétrico", null, null, Instant.now());
        when(service.create(any(), any(), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/service-requests")
                        .with(csrf())
                        .header("X-Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new CreateServiceRequestRequest("ELETRICISTA", "Chuveiro sem funcionar", -3.7319, -38.5267, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.aiDescricaoSugerida").exists());
    }

    @Test
    void createRequest_missingCategoria_returns422() throws Exception {
        mvc.perform(post("/api/v1/service-requests")
                        .with(csrf())
                        .header("X-Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"teste\",\"lat\":-3.7,\"lng\":-38.5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRequest_missingIdempotencyKey_returns400() throws Exception {
        mvc.perform(post("/api/v1/service-requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new CreateServiceRequestRequest("ELETRICISTA", null, -3.7319, -38.5267, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMedia_validMultipart_returns201() throws Exception {
        UUID requestId = UUID.randomUUID();
        var resp = new MediaUploadResponse(UUID.randomUUID(), "https://storage.example.com/foto.jpg", "FOTO");
        when(service.addMedia(eq(requestId), any(), any(), any())).thenReturn(resp);

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", "img".getBytes());
        mvc.perform(multipart("/api/v1/service-requests/{id}/media", requestId)
                        .file(file)
                        .param("tipo", "FOTO")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://storage.example.com/foto.jpg"))
                .andExpect(jsonPath("$.tipo").value("FOTO"));
    }

    @Test
    void meusPedidos_retornaListaDoCliente() throws Exception {
        var dto = new MyRequestDto(UUID.randomUUID(), "ELETRICISTA", "Chuveiro sem funcionar",
                "PROPOSTO", Instant.now(), null, 2, null);
        when(service.listarMeusPedidos(any())).thenReturn(java.util.List.of(dto));

        mvc.perform(get("/api/v1/service-requests/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PROPOSTO"))
                .andExpect(jsonPath("$[0].propostas").value(2));
    }

    @Test
    void detalhe_pedidoExistente_retorna200() throws Exception {
        UUID requestId = UUID.randomUUID();
        var dto = new ServiceRequestDetailDto(requestId, "ACEITO", "ELETRICISTA", "Chuveiro sem funcionar",
                UUID.randomUUID(), UUID.randomUUID(), "Zé Elétrica", Instant.now(), Instant.now(), null);
        when(service.detalhar(eq(requestId), any())).thenReturn(dto);

        mvc.perform(get("/api/v1/service-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestadorNome").value("Zé Elétrica"));
    }

    @Test
    void publish_descricaoValida_retorna200() throws Exception {
        UUID requestId = UUID.randomUUID();
        var dto = new ServiceRequestDto(requestId, "ELETRICISTA", "descrição final",
                "PENDENTE", null, null, null, Instant.now());
        when(service.publicar(eq(requestId), any(), any())).thenReturn(dto);

        mvc.perform(post("/api/v1/service-requests/{id}/publish", requestId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new PublishRequest("descrição final"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("descrição final"));
    }

    @Test
    void publish_descricaoEmBranco_retorna422() throws Exception {
        mvc.perform(post("/api/v1/service-requests/{id}/publish", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
