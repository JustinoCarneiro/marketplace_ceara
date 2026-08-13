package com.onda.marketplace.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.audit.AuditService;
import com.onda.marketplace.shared.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code @PreAuthorize("hasRole('ADMIN')")} não é exercitado aqui: TestSecurityConfig
 * (padrão deste repo pra slices @WebMvcTest) desabilita a checagem de role pra isolar o
 * teste de controller da configuração real de segurança, que é do M01. Este teste cobre
 * o contrato HTTP (status, corpo) e que a auditoria é registrada — não autorização.
 */
@WebMvcTest(CategoryAdminController.class)
@Import(TestSecurityConfig.class)
@SuppressWarnings("null")
class CategoryAdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean CategoryService categoryService;
    @MockBean AuditService    auditService;

    @Test
    void listar_retorna200ComCategorias() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.listar()).thenReturn(List.of(new CategoryDto(id, "Elétrica", "eletrica", true)));

        mvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Elétrica"))
                .andExpect(jsonPath("$[0].slug").value("eletrica"))
                .andExpect(jsonPath("$[0].ativa").value(true));
    }

    @Test
    void criar_retorna201ERegistraAuditoria() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.criar(any())).thenReturn(new CategoryDto(id, "Jardinagem", "jardinagem", true));

        mvc.perform(post("/api/v1/admin/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new CreateCategoryRequest("Jardinagem", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Jardinagem"));

        verify(auditService).registrar(any(), eq("CRIAR_CATEGORIA"), eq("service_category"), eq(id), eq("Jardinagem"));
    }

    @Test
    void criar_semNome_retorna422() throws Exception {
        mvc.perform(post("/api/v1/admin/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void atualizar_retorna200ERegistraAuditoria() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.atualizar(eq(id), any())).thenReturn(new CategoryDto(id, "Elétrica", "eletrica", false));

        mvc.perform(patch("/api/v1/admin/categories/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new UpdateCategoryRequest(null, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));

        verify(auditService).registrar(any(), eq("ATUALIZAR_CATEGORIA"), eq("service_category"), eq(id), eq("Elétrica"));
    }
}
