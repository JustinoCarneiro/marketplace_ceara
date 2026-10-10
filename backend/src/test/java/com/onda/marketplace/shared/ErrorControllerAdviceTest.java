package com.onda.marketplace.shared;

import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.shared.exception.TooManyAttemptsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica que ErrorControllerAdvice produz o envelope padrão
 * { timestamp, status, code, message, path } (TS06).
 * Slice @WebMvcTest — sem banco, sem Flyway.
 */
@WebMvcTest(controllers = StubController.class)
@Import({ErrorControllerAdvice.class, TestSecurityConfig.class})
@SuppressWarnings("null")
class ErrorControllerAdviceTest {

    @Autowired
    MockMvc mvc;

    // ---- 404 ---------------------------------------------------------------

    @Test
    void notFound_returns404WithStandardEnvelope() throws Exception {
        mvc.perform(get("/api/v1/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value("/api/v1/nao-existe"));
    }

    // ---- 422 (validação @Valid) --------------------------------------------

    @Test
    void validationError_returns422WithStandardEnvelope() throws Exception {
        mvc.perform(post("/api/v1/stub/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.path").value("/api/v1/stub/validate"));
    }

    // ---- 400 (JSON malformado) ---------------------------------------------

    @Test
    void malformedJson_returns400WithStandardEnvelope() throws Exception {
        mvc.perform(post("/api/v1/stub/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    // ---- 422 negócio (BusinessException) -----------------------------------

    // ---- 429 (limite de tentativas) ------------------------------------------

    @Test
    void tooManyAttempts_returns429WithRetryAfter_andTheStandardEnvelope() throws Exception {
        mvc.perform(get("/api/v1/stub/too-many"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "840"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.message").value("Muitas tentativas de senha. Tente de novo em 14 minutos."))
                .andExpect(jsonPath("$.path").value("/api/v1/stub/too-many"));
    }

    @Test
    void tooManyAttempts_messageRoundsUpAndUsesTheSingular() {
        // 61 s → "2 minutos" (arredonda para cima: dizer "1" faria a pessoa tentar cedo demais); 1 s → "1 minuto"
        org.assertj.core.api.Assertions.assertThat(new TooManyAttemptsException(61).getMessage()).contains("2 minutos");
        org.assertj.core.api.Assertions.assertThat(new TooManyAttemptsException(1).getMessage()).contains("1 minuto.");
    }

    @Test
    void businessException_returns422WithCustomCode() throws Exception {
        mvc.perform(get("/api/v1/stub/business-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"))
                .andExpect(jsonPath("$.message").isString());
    }

    // ---- 422 corrida por CPF (achado da revisão cruzada, 2026-10-05) -------

    @Test
    void corridaPorCpf_traduzParaCpfAlreadyRegistered_naoDevolve500() throws Exception {
        mvc.perform(get("/api/v1/stub/violacao-cpf"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("CPF_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void outraViolacaoDeIntegridade_naoEhConfundidaComCpf_naoTraduz() {
        // uma restrição qualquer, diferente da do CPF: não pode virar CPF_ALREADY_REGISTERED por engano
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mvc.perform(get("/api/v1/stub/violacao-outra")))
                .hasRootCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}

// Fora da classe de teste para que @WebMvcTest consiga carregar como controller
@RestController
@RequestMapping("/api/v1/stub")
class StubController {

    record ValidRequest(@NotBlank String nome) {}

    @PostMapping("/validate")
    void validate(@Valid @RequestBody ValidRequest req) {}

    @GetMapping("/business-error")
    void businessError() {
        throw new BusinessException("EMAIL_IN_USE", "E-mail já cadastrado.");
    }

    @GetMapping("/too-many")
    void tooMany() {
        throw new TooManyAttemptsException(840);
    }

    @GetMapping("/violacao-cpf")
    void violacaoCpf() {
        throw new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_cpf_hash_key\"");
    }

    @GetMapping("/violacao-outra")
    void violacaoOutra() {
        throw new org.springframework.dao.DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"outra_restricao_qualquer\"");
    }
}
