package com.onda.marketplace.shared.exception;

/**
 * Limite de tentativas de senha atingido: a conta não aceita nova tentativa por um tempo. Vira HTTP 429 com
 * {@code Retry-After} (ver ErrorControllerAdvice). É uma exceção própria — e não só um código de
 * {@link BusinessException} — porque o fluxo que a lança precisa mantê-la fora do rollback (ver
 * {@link PasswordMismatchException}) e a resposta tem status e cabeçalho diferentes dos erros de negócio (422).
 */
public class TooManyAttemptsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("TOO_MANY_ATTEMPTS", mensagem(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** Arredonda para cima: dizer "1 minuto" quando faltam 61 s faria a pessoa tentar cedo demais. */
    private static String mensagem(long segundos) {
        long minutos = Math.max(1, (segundos + 59) / 60);
        return "Muitas tentativas de senha. Tente de novo em " + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }
}
