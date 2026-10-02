package com.onda.marketplace.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onda.marketplace.shared.ErrorControllerAdvice;
import com.onda.marketplace.shared.TestSecurityConfig;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US35 — endpoints de recuperação de senha. Slice @WebMvcTest, serviço mockado: o que se prova
 * aqui é o contrato HTTP (a resposta única, o piso de tempo, a validação). A lógica de segurança
 * está em PasswordResetServiceTest e o fluxo real, contra o Postgres, no E2E.
 */
@WebMvcTest(AuthController.class)
@Import({ErrorControllerAdvice.class, TestSecurityConfig.class})
@TestPropertySource(properties = "marketplace.password-reset.resposta-minima-ms=250")
@SuppressWarnings("null")
class AuthControllerPasswordResetTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean AuthService          authService;
    @MockBean PasswordResetService passwordResetService;

    private String json(Map<String, ?> corpo) throws Exception {
        return mapper.writeValueAsString(corpo);
    }

    // ── POST /auth/forgot-password ──

    @Test
    void forgotPassword_devolve202ComAMensagemPadrao_eChamaOServico() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(ForgotPasswordResponse.MENSAGEM_PADRAO));

        verify(passwordResetService).solicitar("ana@example.com");
    }

    @Test
    void forgotPassword_aRespostaNaoDependeDoQueOServicoFez() throws Exception {
        // O serviço não devolve nada — cadastrado, desconhecido, suspenso e limite excedido
        // terminam igual — e o corpo da resposta é uma constante, não derivada do resultado.
        String corpoCadastrado = mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "cadastrado@example.com"))))
                .andReturn().getResponse().getContentAsString();
        String corpoDesconhecido = mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "desconhecido@example.com"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpoCadastrado).isEqualTo(corpoDesconhecido);
    }

    @Test
    void forgotPassword_esperaUmPisoDeTempo_mesmoQuandoOServicoRetornaNaHora() throws Exception {
        // Sem o piso, a diferença de trabalho entre e-mail cadastrado e desconhecido (gravar o
        // código, em contraste com sair direto) apareceria no tempo de resposta.
        long inicio = System.nanoTime();
        mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana@example.com"))))
                .andExpect(status().isAccepted());
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(ms).isGreaterThanOrEqualTo(240);
    }

    @Test
    void forgotPassword_semEmailConfigurado_avisaIndisponivel_etambemEsperaOPiso() throws Exception {
        doThrow(new BusinessException("PASSWORD_RESET_UNAVAILABLE", "A recuperação de senha está indisponível."))
                .when(passwordResetService).solicitar(any());

        long inicio = System.nanoTime();
        mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana@example.com"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_UNAVAILABLE"));
        assertThat((System.nanoTime() - inicio) / 1_000_000).isGreaterThanOrEqualTo(240);
    }

    @Test
    void forgotPassword_emailMalformado_ehRecusadoNaValidacao() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "isto-nao-e-um-email"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(passwordResetService);
    }

    // ── POST /auth/reset-password ──

    @Test
    void resetPassword_comDadosValidos_devolve204_eRepassaOsTresCampos() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana@example.com", "codigo", "ABCD-2345", "novaSenha", "NovaSenha@1"))))
                .andExpect(status().isNoContent());

        verify(passwordResetService).redefinir("ana@example.com", "ABCD-2345", "NovaSenha@1");
    }

    @Test
    void resetPassword_codigoInvalido_devolve422ComMensagemUnica() throws Exception {
        doThrow(new BusinessException("INVALID_RESET_CODE", "Código inválido ou expirado."))
                .when(passwordResetService).redefinir(any(), any(), any());

        mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana@example.com", "codigo", "ZZZZ9999", "novaSenha", "NovaSenha@1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_CODE"))
                .andExpect(jsonPath("$.message").value("Código inválido ou expirado."));
    }

    @Test
    void resetPassword_senhaCurtaOuLongaDemais_ehRecusadaNaValidacaoSemChegarNoServico() throws Exception {
        for (String senha : new String[] { "curta", "x".repeat(73) }) {
            mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", "ana@example.com", "codigo", "ABCD2345", "novaSenha", senha))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(passwordResetService);
    }

    @Test
    void resetPassword_semCodigo_ehRecusadoNaValidacao() throws Exception {
        mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ana@example.com", "codigo", "", "novaSenha", "NovaSenha@1"))))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(passwordResetService);
    }
}
