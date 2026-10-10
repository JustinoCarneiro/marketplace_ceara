package com.onda.marketplace.auth;

import com.onda.marketplace.shared.ErrorControllerAdvice;
import com.onda.marketplace.shared.TestSecurityConfig;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US36 — endpoint de exclusão de conta. Slice @WebMvcTest, serviço mockado: o que se prova aqui é o
 * contrato HTTP (204, quem é o usuário, validação, o erro que chega ao app). As regras estão em
 * AccountDeletionServiceTest e o efeito no banco, no E2E.
 */
@WebMvcTest(AccountController.class)
@Import({ErrorControllerAdvice.class, TestSecurityConfig.class})
@SuppressWarnings("null")
class AccountControllerTest {

    private static final String URL = "/api/v1/users/me/delete";
    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired MockMvc mvc;
    @MockBean AccountDeletionService service;

    @Test
    void exclui_comASenha_do_usuarioAutenticado_eResponde204SemCorpo() throws Exception {
        mvc.perform(post(URL).with(user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"senha1234\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // o id vem do token, nunca do corpo: ninguém exclui a conta de outra pessoa
        verify(service).excluir(eq(UUID.fromString(USER_ID)), eq("senha1234"));
    }

    @Test
    void senhaEmBranco_retorna422_eNemChamaOServico() throws Exception {
        mvc.perform(post(URL).with(user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void semCorpo_retorna400_eNemChamaOServico() throws Exception {
        mvc.perform(post(URL).with(user(USER_ID)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void senhaIncorreta_chegaAoApp_como422_comOCodigo() throws Exception {
        // 422 e não 401: o app trata 401 como "sessão expirada" e deslogaria quem só errou a senha
        doThrow(new BusinessException("INVALID_PASSWORD", "Senha incorreta."))
                .when(service).excluir(any(), any());

        mvc.perform(post(URL).with(user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"errada123\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
    }

    @Test
    void pendencia_chegaAoApp_comAMensagemDoQueResolver() throws Exception {
        doThrow(new BusinessException("ACCOUNT_HAS_ACTIVE_ORDERS",
                "Você tem pedidos aceitos, em andamento ou em disputa."))
                .when(service).excluir(any(), any());

        mvc.perform(post(URL).with(user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"senha1234\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_ACTIVE_ORDERS"))
                .andExpect(jsonPath("$.message").value("Você tem pedidos aceitos, em andamento ou em disputa."));
    }

    @Test
    void requisicao_naoMostraASenhaNoToString() {
        // o objeto da requisição pode parar num log de depuração: a senha não pode ir junto
        assertThat(new DeleteAccountRequest("senha-secreta-123").toString())
                .doesNotContain("senha-secreta-123");
    }
}
