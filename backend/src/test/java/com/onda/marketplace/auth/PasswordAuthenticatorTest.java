package com.onda.marketplace.auth;

import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.shared.exception.PasswordMismatchException;
import com.onda.marketplace.shared.exception.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Isolado de {@code AccountDeletionServiceTest}/{@code AuthServiceTest} na revisão cruzada (2026-10-05): o acerto/erro
 * de senha virou uma transação PRÓPRIA (REQUIRES_NEW) pra sobreviver a uma recusa de negócio que vem depois, na
 * transação do chamador (ACCOUNT_HAS_ACTIVE_ORDERS, ACCOUNT_SUSPENDED). A transação em si só o E2E prova (mock não
 * executa @Transactional); aqui a LÓGICA — ordem das checagens, idempotência, bloqueio.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PasswordAuthenticatorTest {

    private static final UUID   USER_ID = UUID.randomUUID();
    private static final String EMAIL   = "u@u.com";
    private static final String SENHA   = "senha1234";
    private static final String HASH    = "$2a$hash";

    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;

    PasswordAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new PasswordAuthenticator(userRepository, passwordEncoder, new PasswordAttempts(5, 900));
    }

    private User usuario(UserRole role) {
        return User.builder().email(EMAIL).senhaHash(HASH).role(role).build();
    }

    // ---------- autenticarPorId (exclusão de conta) ----------

    @Test
    void autenticarPorId_contaInexistente_lancaUserNotFound() {
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.autenticarPorId(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }

    @Test
    void autenticarPorId_contaJaExcluida_naoConfereSenha_naoLancaNada() {
        User u = usuario(UserRole.ROLE_CLIENT);
        u.anonimizar("removido@excluido.invalid", "$2a$x", false);
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(u));

        assertThatCode(() -> authenticator.autenticarPorId(USER_ID, SENHA)).doesNotThrowAnyException();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void autenticarPorId_admin_recusaAntesDeConferirSenha() {
        User u = usuario(UserRole.ROLE_ADMIN);
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authenticator.autenticarPorId(USER_ID, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ADMIN_CANNOT_DELETE");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void autenticarPorId_senhaErrada_contaOErro_eLancaComOCodigoDaExclusao() {
        User u = usuario(UserRole.ROLE_CLIENT);
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(SENHA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticator.autenticarPorId(USER_ID, SENHA))
                .isInstanceOf(PasswordMismatchException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PASSWORD");   // não INVALID_CREDENTIALS (login)
        assertThat(u.getSenhaFalhas()).isEqualTo(1);
    }

    @Test
    void autenticarPorId_bloqueada_nemConfereASenhaCerta() {
        User u = usuario(UserRole.ROLE_CLIENT);
        u.registrarSenhaErrada(java.time.Instant.now(), 1, java.time.Duration.ofMinutes(15));
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> authenticator.autenticarPorId(USER_ID, SENHA))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void autenticarPorId_senhaCerta_zeraOContador() {
        User u = usuario(UserRole.ROLE_CLIENT);
        u.registrarSenhaErrada(java.time.Instant.now().minusSeconds(1000), 5, java.time.Duration.ofSeconds(1));
        when(userRepository.findByIdComTrava(USER_ID)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(SENHA, HASH)).thenReturn(true);

        authenticator.autenticarPorId(USER_ID, SENHA);

        assertThat(u.getSenhaFalhas()).isZero();
    }

    // ---------- autenticarPorEmail (login) ----------

    @Test
    void autenticarPorEmail_emailDesconhecido_lancaInvalidCredentials_semContarNada() {
        when(userRepository.findByEmailComTrava(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.autenticarPorEmail(EMAIL, SENHA))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
    }

    @Test
    void autenticarPorEmail_senhaErrada_contaOErro_eLancaComOCodigoDoLogin() {
        User u = usuario(UserRole.ROLE_CLIENT);
        when(userRepository.findByEmailComTrava(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(SENHA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticator.autenticarPorEmail(EMAIL, SENHA))
                .isInstanceOf(PasswordMismatchException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");   // não INVALID_PASSWORD (exclusão)
        assertThat(u.getSenhaFalhas()).isEqualTo(1);
    }

    @Test
    void autenticarPorEmail_senhaCerta_zeraOContador() {
        User u = usuario(UserRole.ROLE_CLIENT);
        when(userRepository.findByEmailComTrava(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(SENHA, HASH)).thenReturn(true);

        authenticator.autenticarPorEmail(EMAIL, SENHA);

        assertThat(u.getSenhaFalhas()).isZero();
    }

    @Test
    void ambosOsMetodos_saoRequiresNewComONoRollbackForDoLimite() throws Exception {
        for (var metodo : new String[] {"autenticarPorEmail", "autenticarPorId"}) {
            Class<?> chave = metodo.equals("autenticarPorEmail") ? String.class : UUID.class;
            var m = PasswordAuthenticator.class.getMethod(metodo, chave, String.class);
            var tx = m.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertThat(tx.propagation()).as(metodo).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
            assertThat(tx.noRollbackFor()).as(metodo)
                    .containsExactlyInAnyOrder(PasswordMismatchException.class, TooManyAttemptsException.class);
        }
    }
}
