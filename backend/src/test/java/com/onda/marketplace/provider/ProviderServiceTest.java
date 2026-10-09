package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.auth.JwtService;
import com.onda.marketplace.auth.RefreshTokenRepository;
import com.onda.marketplace.auth.TermsAcceptanceRepository;
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
    @Mock TermsAcceptanceRepository termsAcceptanceRepository;

    ProviderService providerService;

    @BeforeEach
    void setUp() {
        var encoder   = new BCryptPasswordEncoder();
        var encryptor = new CpfEncryptor("01234567890123456789012345678901");
        providerService = new ProviderService(
                userRepository, profileRepository, refreshTokenRepository,
                jwtService, encoder, encryptor, backgroundCheckService, termsAcceptanceRepository, 30L);
    }

    @Test
    void register_createsUserWithRoleProvider() {
        var req = new RegisterProviderRequest(
                "Carlos", "carlos@test.com", "Senha@123", "999.999.999-99", "ELETRICISTA", null, true);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("tok");

        AuthResponse resp = providerService.register(req, "203.0.113.5");

        assertThat(resp.role()).isEqualTo("ROLE_PROVIDER");
        verify(backgroundCheckService).scheduleCheck(any());
        verify(termsAcceptanceRepository).save(any());
    }

    @Test
    void register_cpfNeverStoredAsPlainText() {
        var req = new RegisterProviderRequest(
                "Carlos", "c2@test.com", "Senha@123", "123.456.789-09", "ELETRICISTA", null, true);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> {
            ProviderProfile p = i.getArgument(0);
            assertThat(p.getCpfCifrado()).doesNotContain("123.456.789-09");
            return p;
        });
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("tok");

        providerService.register(req, "203.0.113.5");
        verify(profileRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throwsBusinessException() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> providerService.register(
                new RegisterProviderRequest("X", "dup@test.com", "P@ss1", "000", "EL", null, true), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_IN_USE");
    }

    /** Dono vivo do perfil: a trava do usuário é a PRIMEIRA leitura de atualizarChavePix (revisão cruzada, 2ª rodada). */
    private void donoVivo(java.util.UUID userId) {
        when(userRepository.findByIdComTrava(userId)).thenReturn(java.util.Optional.of(
                com.onda.marketplace.auth.User.builder().nome("Prestador").email("p@pix.test").senhaHash("$2a$x")
                        .role(com.onda.marketplace.auth.UserRole.ROLE_PROVIDER).build()));
    }

    @Test
    void atualizarChavePix_cifraAntesDeSalvar_naoGuardaEmClaro() {
        var userId = java.util.UUID.randomUUID();
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
        donoVivo(userId);
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(perfil));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.atualizarChavePix(userId, "prestador@pix.com");

        assertThat(perfil.getChavePixCifrada())
                .isNotBlank()
                .doesNotContain("prestador@pix.com");
        verify(profileRepository).save(perfil);
    }

    @Test
    void atualizarChavePix_vazia_ehRejeitada() {
        assertThatThrownBy(() -> providerService.atualizarChavePix(java.util.UUID.randomUUID(), "  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PIX_KEY_REQUIRED");
        verifyNoInteractions(profileRepository);
    }

    @Test
    void atualizarChavePix_perfilInexistente_404() {
        var userId = java.util.UUID.randomUUID();
        donoVivo(userId);
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> providerService.atualizarChavePix(userId, "chave"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_FOUND");
    }

    @Test
    void atualizarChavePix_chaveMalFormada_ehRejeitadaESemSalvar() {
        var userId = java.util.UUID.randomUUID();
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
        donoVivo(userId);
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(perfil));

        assertThatThrownBy(() -> providerService.atualizarChavePix(userId, "minha chave qualquer"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PIX_KEY_INVALID");

        assertThat(perfil.getChavePixCifrada()).isNull();
        verify(profileRepository, never()).save(any());
    }

    @Test
    void atualizarChavePix_guardaAChaveNormalizada_noMesmoFormatoQueORepasseVaiUsar() {
        var userId = java.util.UUID.randomUUID();
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
        donoVivo(userId);
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(perfil));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.atualizarChavePix(userId, "  111.444.777-35 ");

        var decifrador = new CpfEncryptor("01234567890123456789012345678901");
        assertThat(decifrador.decrypt(perfil.getChavePixCifrada())).isEqualTo("11144477735");
    }

    @Test
    void atualizarChavePix_contaExcluida_naoGrava_eNaoLePerfil() {
        // exclusão que commitou antes de a trava ser obtida: o perfil já está anonimizado, nada se grava nele
        var userId = java.util.UUID.randomUUID();
        var excluido = com.onda.marketplace.auth.User.builder().nome("Prestador").email("x@pix.test").senhaHash("$2a$x")
                .role(com.onda.marketplace.auth.UserRole.ROLE_PROVIDER).build();
        excluido.anonimizar("removido-pix@excluido.invalid", "hash-inutilizavel", false);
        when(userRepository.findByIdComTrava(userId)).thenReturn(java.util.Optional.of(excluido));

        assertThatThrownBy(() -> providerService.atualizarChavePix(userId, "prestador@pix.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_FOUND");
        verifyNoInteractions(profileRepository);
    }

    @Test
    void atualizarChavePix_travaOUsuarioAntesDeLerOPerfil_aTravaEAPrimeiraLeitura() {
        // a trava só vale como primeira leitura (o Hibernate devolve a instância antiga se já carregada): ordem importa
        var userId = java.util.UUID.randomUUID();
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
        donoVivo(userId);
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(perfil));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.atualizarChavePix(userId, "prestador@pix.com");

        var ordem = inOrder(userRepository, profileRepository);
        ordem.verify(userRepository).findByIdComTrava(userId);
        ordem.verify(profileRepository).findByUserId(userId);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void chavePixCadastrada_refleteOEstadoDoPerfil() {
        var comChave = new ProviderProfile(null, "EL", "cpf");
        comChave.setChavePixCifrada("cifrado");
        var semChave = new ProviderProfile(null, "EL", "cpf");
        var u1 = java.util.UUID.randomUUID();
        var u2 = java.util.UUID.randomUUID();
        when(profileRepository.findByUserId(u1)).thenReturn(java.util.Optional.of(comChave));
        when(profileRepository.findByUserId(u2)).thenReturn(java.util.Optional.of(semChave));

        assertThat(providerService.chavePixCadastrada(u1)).isTrue();
        assertThat(providerService.chavePixCadastrada(u2)).isFalse();
    }
}
