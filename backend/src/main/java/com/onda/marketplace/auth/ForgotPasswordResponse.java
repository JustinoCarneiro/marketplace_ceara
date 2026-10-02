package com.onda.marketplace.auth;

/**
 * Resposta ÚNICA do pedido de recuperação (US35): a mesma para e-mail cadastrado, desconhecido,
 * conta suspensa e limite excedido — o texto nunca revela se o e-mail existe.
 */
public record ForgotPasswordResponse(String mensagem) {

    static final String MENSAGEM_PADRAO =
            "Se o e-mail estiver cadastrado, enviamos um código para ele. O código vale por 30 minutos.";

    public static ForgotPasswordResponse padrao() {
        return new ForgotPasswordResponse(MENSAGEM_PADRAO);
    }
}
