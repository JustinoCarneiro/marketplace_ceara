package com.onda.marketplace.auth;

import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class AuthServiceTest {

    @Mock UserRepository        userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService            jwtService;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock CpfHashService        cpfHashService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, jwtService,
                passwordEncoder, cpfHashService, 30L);
    }

    @Test
    void registerClient_passwordNeverStoredAsPlainText() {
        var req = new RegisterClientRequest("Ana", "ana@example.com", "Senha@123");
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("access");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.registerClient(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getSenhaHash())
                .as("Senha nunca deve ser armazenada em texto puro")
                .isNotEqualTo("Senha@123")
                .startsWith("$2a$");
    }

    @Test
    void registerClient_duplicateEmail_throwsBusinessException() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.registerClient(
                        new RegisterClientRequest("X", "dup@example.com", "Senha@123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_IN_USE");
    }

    @Test
    void login_wrongPassword_throwsBusinessException() {
        var user = User.builder()
                .email("u@u.com")
                .senhaHash("$2a$hash")
                .role(UserRole.ROLE_CLIENT)
                .build();
        when(userRepository.findByEmail("u@u.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("u@u.com", "errada")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
    }

    @Test
    void refresh_invalidToken_throwsBusinessException() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("token-invalido")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
    }

    // Antifraude Camada 2 (PENDENCIAS_INTEGRIDADE.md): CPF único na plataforma — sem isto,
    // a mesma pessoa cria uma segunda conta pra se auto-contratar e fabricar reputação.

    @Test
    void verifyIdentity_cpfJaVinculadoAOutraConta_lancaCpfAlreadyRegistered() {
        UUID userId = UUID.randomUUID();
        var user = User.builder().email("u@u.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        when(cpfHashService.hash("11122233344")).thenReturn("hash-existente");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByCpfHash("hash-existente")).thenReturn(true);

        assertThatThrownBy(() -> authService.verifyIdentity("11122233344", userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_ALREADY_REGISTERED");
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyIdentity_retryComMesmoCpfJaVerificado_naoLancaEhIdempotente() {
        // Regressão: antes, checar existsByCpfHash ANTES do hash do próprio usuário fazia um
        // retry com o mesmo CPF colidir com o próprio registro e vazar CPF_ALREADY_REGISTERED.
        UUID userId = UUID.randomUUID();
        var user = User.builder().email("u@u.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        user.setCpfHash("hash-ja-verificado");
        when(cpfHashService.hash("11122233344")).thenReturn("hash-ja-verificado");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatCode(() -> authService.verifyIdentity("11122233344", userId)).doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyIdentity_cpfNovo_vinculaAoUsuario() {
        UUID userId = UUID.randomUUID();
        var user = User.builder().email("u@u.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        when(cpfHashService.hash("11122233344")).thenReturn("hash-novo");
        when(userRepository.existsByCpfHash("hash-novo")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.verifyIdentity("11122233344", userId);

        assertThat(user.getCpfHash()).isEqualTo("hash-novo");
        verify(userRepository).save(user);
    }
}
