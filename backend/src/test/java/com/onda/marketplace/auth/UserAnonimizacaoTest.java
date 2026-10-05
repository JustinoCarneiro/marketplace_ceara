package com.onda.marketplace.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exclusão de conta (US36): a linha de `users` fica, sem nenhum dado pessoal. */
class UserAnonimizacaoTest {

    private static User usuario() {
        User u = User.builder().nome("Maria Silva").email("maria@exemplo.com")
                .senhaHash("$2a$hash-da-senha").role(UserRole.ROLE_CLIENT).build();
        u.setCpfHash("hmac-do-cpf");
        return u;
    }

    @Test
    void anonimizar_removeTodoDadoPessoalEBloqueiaAConta() {
        User u = usuario();

        u.anonimizar("removido-1@excluido.invalid", "$2a$hash-inutilizavel", false);

        assertThat(u.getNome()).isEqualTo("Usuário removido");
        assertThat(u.getEmail()).isEqualTo("removido-1@excluido.invalid");
        assertThat(u.getSenhaHash()).isEqualTo("$2a$hash-inutilizavel");
        assertThat(u.getCpfHash()).isNull();
        assertThat(u.isAtivo()).isFalse();
        assertThat(u.isExcluido()).isTrue();
        assertThat(u.getExcluidoEm()).isNotNull();
    }

    @Test
    void anonimizar_mantemOHashDoCpfQuandoPedido_antifraude() {
        // conta suspensa / prestador reprovado: excluir não pode ser um jeito de burlar o banimento
        User u = usuario();

        u.anonimizar("removido-2@excluido.invalid", "$2a$x", true);

        assertThat(u.getCpfHash()).isEqualTo("hmac-do-cpf");
        assertThat(u.getNome()).isEqualTo("Usuário removido");
    }

    @Test
    void anonimizar_duasVezes_naoRefazNada() {
        User u = usuario();
        u.anonimizar("removido-3@excluido.invalid", "$2a$x", false);
        var primeiraExclusao = u.getExcluidoEm();

        u.anonimizar("outro@excluido.invalid", "$2a$y", false);

        assertThat(u.getEmail()).isEqualTo("removido-3@excluido.invalid");
        assertThat(u.getExcluidoEm()).isEqualTo(primeiraExclusao);
    }

    @Test
    void contaExcluida_naoPodeSerReativada() {
        User u = usuario();
        u.anonimizar("removido-4@excluido.invalid", "$2a$x", false);

        assertThatThrownBy(u::reativar).isInstanceOf(IllegalStateException.class);
        assertThat(u.isAtivo()).isFalse();
    }

    @Test
    void contaNormal_continuaPodendoSerSuspensaEReativada() {
        User u = usuario();

        u.suspender();
        assertThat(u.isAtivo()).isFalse();
        u.reativar();

        assertThat(u.isAtivo()).isTrue();
        assertThat(u.isExcluido()).isFalse();
    }
}
