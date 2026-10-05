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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes do AuthController — slice @WebMvcTest, AuthService mockado.
 * Cobre US01 (cadastro cliente), US12 (login, refresh token).
 */
@WebMvcTest(AuthController.class)
@Import({ErrorControllerAdvice.class, TestSecurityConfig.class})
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @MockBean AuthService authService;
    // o controller também serve a recuperação de senha (US35) — testada em AuthControllerPasswordResetTest
    @MockBean PasswordResetService passwordResetService;

    static final AuthResponse FAKE_RESPONSE = new AuthResponse(
            "access.token.jwt", "refresh-uuid-token", "ROLE_CLIENT",
            java.util.UUID.randomUUID(), "Cliente Teste", "cliente@test.com", java.util.List.of("ROLE_CLIENT")
    );

    // ── US01: cadastro de cliente ────────────────────────────────────────────

    @Test
    void registerClient_validData_returns201WithTokens() throws Exception {
        when(authService.registerClient(any(), any())).thenReturn(FAKE_RESPONSE);

        mvc.perform(post("/api/v1/auth/register/client")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "nome", "João Silva",
                                "email", "joao@example.com",
                                "senha", "Senha@123",
                                "aceitouTermos", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.role").value("ROLE_CLIENT"));
    }

    @Test
    void registerClient_duplicateEmail_returns422EmailInUse() throws Exception {
        when(authService.registerClient(any(), any()))
                .thenThrow(new BusinessException("EMAIL_IN_USE", "E-mail já cadastrado."));

        mvc.perform(post("/api/v1/auth/register/client")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "nome", "Maria",
                                "email", "duplicado@example.com",
                                "senha", "Senha@123",
                                "aceitouTermos", true
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
    }

    @Test
    void registerClient_missingFields_returns422ValidationError() throws Exception {
        mvc.perform(post("/api/v1/auth/register/client")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void registerClient_termosNaoAceitos_returns422ValidationError() throws Exception {
        // docs/PENDENCIAS_JURIDICAS.md item 3 — sem aceite não há cadastro.
        mvc.perform(post("/api/v1/auth/register/client")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "nome", "João Silva",
                                "email", "joao@example.com",
                                "senha", "Senha@123",
                                "aceitouTermos", false
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ── US12: login ──────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        when(authService.login(any())).thenReturn(FAKE_RESPONSE);

        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "joao@example.com",
                                "senha", "Senha@123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.role").isString());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException("INVALID_CREDENTIALS", "Credenciais inválidas."));

        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "joao@example.com",
                                "senha", "errada"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ── US12: refresh token ──────────────────────────────────────────────────

    @Test
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        when(authService.refresh(any())).thenReturn(FAKE_RESPONSE);

        mvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "refreshToken", "refresh-uuid-token"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void refresh_expiredOrRevokedToken_returns401() throws Exception {
        when(authService.refresh(any()))
                .thenThrow(new BusinessException("INVALID_REFRESH_TOKEN", "Token inválido ou expirado."));

        mvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "refreshToken", "token-expirado"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    // ── Conta única com papéis: troca de contexto da sessão

    @Test
    void switchRole_trocaOPapel_usandoOIdDoTokenENaoODoCorpo() throws Exception {
        java.util.UUID doToken = java.util.UUID.randomUUID();
        when(authService.switchRole(eq(doToken), any())).thenReturn(new AuthResponse(
                "novo.access", "novo-refresh", "ROLE_PROVIDER", doToken, "Duda", "duda@test.com",
                java.util.List.of("ROLE_CLIENT", "ROLE_PROVIDER")));

        mvc.perform(post("/api/v1/auth/switch-role")
                        .with(csrf()).with(user(doToken.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"papel\":\"ROLE_PROVIDER\",\"refreshToken\":\"antigo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_PROVIDER"))
                .andExpect(jsonPath("$.papeis[0]").value("ROLE_CLIENT"))
                .andExpect(jsonPath("$.papeis[1]").value("ROLE_PROVIDER"));

        // quem troca é o dono do token: nada no corpo escolhe a conta
        verify(authService).switchRole(eq(doToken), any());
    }

    @Test
    void switchRole_papelEmBranco_returns422() throws Exception {
        mvc.perform(post("/api/v1/auth/switch-role")
                        .with(csrf()).with(user(java.util.UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"papel\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void switchRole_papelQueAContaNaoTem_returns422ComOCodigo() throws Exception {
        when(authService.switchRole(any(), any())).thenThrow(new BusinessException("ROLE_NOT_AVAILABLE", "Esta conta ainda não é de prestador."));

        mvc.perform(post("/api/v1/auth/switch-role")
                        .with(csrf()).with(user(java.util.UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"papel\":\"ROLE_PROVIDER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_AVAILABLE"));
    }
}
