package com.onda.marketplace.auth;

/** A senha foi trocada pela recuperação (US35): dispara o aviso por e-mail ao dono da conta. */
public record PasswordChanged(String email, String nome) {

    @Override
    public String toString() {
        return "PasswordChanged[***]";
    }
}
