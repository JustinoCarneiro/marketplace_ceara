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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository        userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtService            jwtService;
    @Mock PasswordEncoder       passwordEncoder;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, jwtService, passwordEncoder, 30L);
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
}
