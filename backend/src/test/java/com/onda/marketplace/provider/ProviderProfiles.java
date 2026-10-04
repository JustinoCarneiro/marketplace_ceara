package com.onda.marketplace.provider;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRole;

/** Monta perfis de prestador em cada status, para os testes da regra de verificação. */
public final class ProviderProfiles {

    private ProviderProfiles() {}

    public static ProviderProfile comStatus(ProviderStatus status) {
        User user = User.builder()
                .nome("Prestador Teste").email("prestador@test.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build();
        ProviderProfile perfil = new ProviderProfile(user, "Elétrica", "cpf-cifrado");
        switch (status) {
            case VERIFICADO -> perfil.aprovar();
            case REPROVADO  -> perfil.reprovar();
            case SUSPENSO   -> perfil.suspender();
            case EM_VERIFICACAO -> { /* estado inicial do cadastro */ }
        }
        return perfil;
    }
}
