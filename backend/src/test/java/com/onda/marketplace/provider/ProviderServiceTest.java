package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.auth.JwtService;
import com.onda.marketplace.auth.RefreshTokenRepository;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ProviderServiceTest {

    @Mock UserRepository           userRepository;
    @Mock ProviderProfileRepository profileRepository;
    @Mock RefreshTokenRepository   refreshTokenRepository;
    @Mock JwtService               jwtService;
    @Mock BackgroundCheckService   backgroundCheckService;

    ProviderService providerService;

    @BeforeEach
    void setUp() {
        var encoder   = new BCryptPasswordEncoder();
        var encryptor = new CpfEncryptor("01234567890123456789012345678901");
        providerService = new ProviderService(
                userRepository, profileRepository, refreshTokenRepository,
                jwtService, encoder, encryptor, backgroundCheckService, 30L);
    }

    @Test
    void register_createsUserWithRoleProvider() {
        var req = new RegisterProviderRequest(
                "Carlos", "carlos@test.com", "Senha@123", "999.999.999-99", "ELETRICISTA");
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("tok");

        AuthResponse resp = providerService.register(req);

        assertThat(resp.role()).isEqualTo("ROLE_PROVIDER");
        verify(backgroundCheckService).scheduleCheck(any());
    }

    @Test
    void register_cpfNeverStoredAsPlainText() {
        var req = new RegisterProviderRequest(
                "Carlos", "c2@test.com", "Senha@123", "123.456.789-09", "ELETRICISTA");
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> {
            ProviderProfile p = i.getArgument(0);
            assertThat(p.getCpfCifrado()).doesNotContain("123.456.789-09");
            return p;
        });
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("tok");

        providerService.register(req);
        verify(profileRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throwsBusinessException() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> providerService.register(
                new RegisterProviderRequest("X", "dup@test.com", "P@ss1", "000", "EL")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_IN_USE");
    }
}
