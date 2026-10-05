package com.onda.marketplace.auth;

/**
 * A conta foi excluída (US36): dispara o aviso por e-mail ao dono. Leva o e-mail e o nome de ANTES da
 * anonimização — depois dela eles não existem mais em lugar nenhum.
 */
public record AccountDeleted(String email, String nome) {

    @Override
    public String toString() {
        return "AccountDeleted[***]";
    }
}
