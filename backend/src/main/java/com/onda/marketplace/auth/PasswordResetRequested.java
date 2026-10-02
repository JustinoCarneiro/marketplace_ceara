package com.onda.marketplace.auth;

/**
 * Um código de recuperação foi emitido (US35). Carrega o código em claro SÓ em memória, entre o
 * commit e o e-mail — por isso o {@code toString} não imprime nada: se alguém logar o evento por
 * descuido, o código e o endereço não saem junto.
 */
public record PasswordResetRequested(String email, String nome, String codigo, long validadeMinutos) {

    @Override
    public String toString() {
        return "PasswordResetRequested[***]";
    }
}
