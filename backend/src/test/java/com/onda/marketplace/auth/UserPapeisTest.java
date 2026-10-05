package com.onda.marketplace.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Conta única com papéis (antifraude Camada 3): a mesma pessoa é cliente e prestador na MESMA conta. */
class UserPapeisTest {

    private static User com(UserRole role) {
        return User.builder().nome("Duda").email("duda@x.com").senhaHash("$2a$x").role(role).build();
    }

    @Test
    void cliente_temSoOPapelDeCliente() {
        var u = com(UserRole.ROLE_CLIENT);

        assertThat(u.getPapeis()).containsExactly(UserRole.ROLE_CLIENT);
        assertThat(u.temPapel(UserRole.ROLE_PROVIDER)).isFalse();
    }

    @Test
    void prestador_tambemTemOPapelDeCliente_todoPrestadorContrata() {
        var u = com(UserRole.ROLE_PROVIDER);

        assertThat(u.getPapeis()).containsExactlyInAnyOrder(UserRole.ROLE_PROVIDER, UserRole.ROLE_CLIENT);
        assertThat(u.getRole()).as("o principal continua sendo o do cadastro").isEqualTo(UserRole.ROLE_PROVIDER);
    }

    @Test
    void admin_temSoOPapelDeAdmin_naoContrataNemPresta() {
        var u = com(UserRole.ROLE_ADMIN);

        assertThat(u.getPapeis()).containsExactly(UserRole.ROLE_ADMIN);
    }

    @Test
    void clienteQuePassaAPrestar_ganhaOPapel_semPerderOdeCliente() {
        var u = com(UserRole.ROLE_CLIENT);

        u.concederPapel(UserRole.ROLE_PROVIDER);

        assertThat(u.getPapeis()).containsExactlyInAnyOrder(UserRole.ROLE_CLIENT, UserRole.ROLE_PROVIDER);
        assertThat(u.getRole()).as("o papel principal não muda: é o do cadastro").isEqualTo(UserRole.ROLE_CLIENT);
    }

    @Test
    void getPapeis_naoDeixaMexerNoConjuntoPorFora() {
        var u = com(UserRole.ROLE_CLIENT);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> u.getPapeis().add(UserRole.ROLE_ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void semPapelPrincipal_naoGravaPapelNulo() {
        var u = User.builder().nome("X").email("x@x.com").senhaHash("$2a$x").build();

        assertThat(u.getPapeis()).isEmpty();
    }

    @Test
    void vincularCpf_guardaOHashEAVersaoDaChave() {
        var u = com(UserRole.ROLE_CLIENT);
        assertThat(u.getCpfHashVersao()).as("conta sem CPF começa na 1, a das contas anteriores à separação").isEqualTo(1);

        u.vincularCpf("hash", 2);

        assertThat(u.getCpfHash()).isEqualTo("hash");
        assertThat(u.getCpfHashVersao()).isEqualTo(2);
    }
}
