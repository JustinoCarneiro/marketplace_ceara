package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.JwtService;
import com.onda.marketplace.auth.RefreshTokenRepository;
import com.onda.marketplace.auth.TermsAcceptanceRepository;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
                jwtService, encoder, encryptor, new CpfHashService("chave-hmac-de-teste-com-mais-de-32-caracteres!"),
                backgroundCheckService, termsAcceptanceRepository, 30L);
    }

    @Test
    void register_createsUserWithRoleProvider() {
        var req = new RegisterProviderRequest(
                "Carlos", "carlos@test.com", "Senha@123", "111.444.777-35", "ELETRICISTA", null, true);
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

    // ── Uma pessoa = um CPF: o cadastro do prestador também grava o hash (antes só o cliente, no 1º pagamento) e recusa duplicata.

    private RegisterProviderRequest comCpf(String cpf, String email) {
        return new RegisterProviderRequest("Carlos", email, "Senha@123", cpf, "ELETRICISTA", null, true);
    }

    private void cadastroPossivel() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any())).thenReturn("tok");
    }

    @Test
    void register_gravaOHashDoCpf_naConta_eSoOHash() {
        cadastroPossivel();
        when(userRepository.existsByCpfHash(any())).thenReturn(false);

        providerService.register(comCpf("111.444.777-35", "h@test.com"), "203.0.113.5");

        ArgumentCaptor<User> usuario = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(usuario.capture());
        assertThat(usuario.getValue().getCpfHash()).hasSize(64).doesNotContain("111").doesNotContain("444");
    }

    @Test
    void register_cpfComEsemMascara_geraOMesmoHash_umaPessoaUmaIdentidade() {
        cadastroPossivel();
        when(userRepository.existsByCpfHash(any())).thenReturn(false);
        providerService.register(comCpf("111.444.777-35", "m1@test.com"), "203.0.113.5");
        providerService.register(comCpf("11144477735", "m2@test.com"), "203.0.113.5");

        ArgumentCaptor<User> usuarios = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(usuarios.capture());
        assertThat(usuarios.getAllValues().get(0).getCpfHash()).isEqualTo(usuarios.getAllValues().get(1).getCpfHash());
    }

    @Test
    void register_cpfJaVinculadoAOutraConta_recusa_eNadaEhGravado() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByCpfHash(any())).thenReturn(true);

        assertThatThrownBy(() -> providerService.register(comCpf("111.444.777-35", "d@test.com"), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_ALREADY_REGISTERED");
        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
        verify(backgroundCheckService, never()).scheduleCheck(any());
    }

    @Test
    void register_cpfInvalido_recusa_semConsultarNemGravarNada() {
        when(userRepository.existsByEmail(any())).thenReturn(false);

        // dígito verificador errado e repetido: sem validar, um número inventado burlaria a unicidade
        for (String invalido : new String[] {"123.456.789-00", "111.111.111-11", "abc"}) {
            assertThatThrownBy(() -> providerService.register(comCpf(invalido, "i@test.com"), "203.0.113.5"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_CPF");
        }
        verify(userRepository, never()).existsByCpfHash(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsBusinessException() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> providerService.register(
                new RegisterProviderRequest("X", "dup@test.com", "P@ss1", "000", "EL", null, true), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_IN_USE");
    }

    @Test
    void atualizarChavePix_cifraAntesDeSalvar_naoGuardaEmClaro() {
        var userId = java.util.UUID.randomUUID();
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
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
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> providerService.atualizarChavePix(userId, "chave"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PROVIDER_NOT_FOUND");
    }

    @Test
    void atualizarChavePix_chaveMalFormada_ehRejeitadaESemSalvar() {
        var userId = java.util.UUID.randomUUID();
        var perfil = new ProviderProfile(null, "ELETRICISTA", "cpf-cifrado");
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
        when(profileRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(perfil));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.atualizarChavePix(userId, "  111.444.777-35 ");

        var decifrador = new CpfEncryptor("01234567890123456789012345678901");
        assertThat(decifrador.decrypt(perfil.getChavePixCifrada())).isEqualTo("11144477735");
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
