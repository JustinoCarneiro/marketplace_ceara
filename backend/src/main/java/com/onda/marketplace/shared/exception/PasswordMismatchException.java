package com.onda.marketplace.shared.exception;

/**
 * Senha errada (login: {@code INVALID_CREDENTIALS}; exclusão de conta: {@code INVALID_PASSWORD}). Continua sendo um
 * erro de negócio (422), mas é uma classe própria para o fluxo que a lança declarar {@code noRollbackFor}: o contador
 * de erros precisa ser gravado MESMO com a exceção — sem isso a transação voltava inteira e o limite nunca valia.
 * Específica de propósito: com {@code noRollbackFor = BusinessException} qualquer outra recusa de negócio lançada
 * depois de uma escrita parcial também seria confirmada.
 */
public class PasswordMismatchException extends BusinessException {

    public PasswordMismatchException(String code, String message) {
        super(code, message);
    }
}
