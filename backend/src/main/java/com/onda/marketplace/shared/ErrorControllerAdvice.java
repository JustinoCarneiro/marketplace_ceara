package com.onda.marketplace.shared;

import com.onda.marketplace.shared.error.ApiError;
import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.shared.exception.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ErrorControllerAdvice {

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNotFound(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiError.of(404, "NOT_FOUND", "Recurso não encontrado.", req.getRequestURI())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Dados inválidos.");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiError.of(422, "VALIDATION_ERROR", message, req.getRequestURI())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleBadRequest(HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiError.of(400, "BAD_REQUEST", "Corpo da requisição inválido.", req.getRequestURI())
        );
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(429, ex.getCode(), ex.getMessage(), req.getRequestURI()));
    }

    /**
     * Corrida por CPF (achado da revisão cruzada, 2026-10-05): dois cadastros com o mesmo CPF passam pela consulta de
     * duplicata antes de qualquer gravação; a restrição UNIQUE (users_cpf_hash_key) impede a dupla, mas a 2ª transação
     * caía sem tradução — 500, não 422. A corrida é rara (precisa de dois cadastros no mesmo instante), mas o CPF é
     * exatamente o campo que isto protege: traduzido aqui, não no caminho feliz do cadastro.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest req) {
        String causa = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (causa.contains("users_cpf_hash_key")) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                    ApiError.of(422, "CPF_ALREADY_REGISTERED", "Este CPF já está vinculado a outra conta.", req.getRequestURI())
            );
        }
        throw ex;   // outra violação: não esconder um bug diferente atrás do mesmo rótulo
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiError.of(422, ex.getCode(), ex.getMessage(), req.getRequestURI())
        );
    }
}
