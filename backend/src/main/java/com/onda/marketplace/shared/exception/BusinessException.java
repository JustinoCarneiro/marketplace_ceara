package com.onda.marketplace.shared.exception;

/**
 * Exceção de regra de negócio → 422 com código semântico.
 * Ex.: throw new BusinessException("EMAIL_IN_USE", "E-mail já cadastrado.");
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
