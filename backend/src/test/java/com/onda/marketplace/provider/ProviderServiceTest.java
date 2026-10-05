package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.auth.AuthService;
import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.auth.JwtService;
import com.onda.marketplace.auth.PasswordAttempts;
import com.onda.marketplace.auth.RefreshToken;
import com.onda.marketplace.auth.RefreshTokenRepository;
import com.onda.marketplace.auth.TermsAcceptance;
import com.onda.marketplace.auth.TermsAcceptanceRepository;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

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

    static final String CHAVE_HASH = "test-cpf-hmac-key-0123456789-0123456789";

    final CpfHashService cpfHashService = new CpfHashService(CHAVE_HASH, 2);
    ProviderService providerService;

    @BeforeEach
    void setUp() {
        var encoder   = new BCryptPasswordEncoder();
        var encryptor = new CpfEncryptor("01234567890123456789012345678901");
        // AuthService REAL sobre repositórios mockados: é ele quem vincula o CPF e emite a sessão, e o que se prova aqui é
        // exatamente essa integração (papéis da conta, contexto do token, hash do CPF)
        var authService = new AuthService(userRepository, refreshTokenRepository, jwtService, encoder,
                cpfHashService, termsAcceptanceRepository, new PasswordAttempts(5, 900), 30L);
        providerService = new ProviderService(userRepository, profileRepository, authService, encoder, encryptor,
                backgroundCheckService, termsAcceptanceRepository);
    }

    private void cadastroPossivel() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("tok");
    }

    private RegisterProviderRequest comCpf(String cpf, String email) {
        return new RegisterProviderRequest("Carlos", email, "Senha@123", cpf, "ELETRICISTA", null, true);
    }

    @Test
    void register_criaContaDePrestador_queTambemTemOPapelDeCliente_eAbreNoContextoDePrestador() {
        cadastroPossivel();

        AuthResponse resp = providerService.register(comCpf("111.444.777-35", "carlos@test.com"), "203.0.113.5");

        // conta única com papéis: todo prestador também contrata (o prestador que precisa de um eletricista em casa)
        assertThat(resp.role()).isEqualTo("ROLE_PROVIDER");
        assertThat(resp.papeis()).containsExactly("ROLE_CLIENT", "ROLE_PROVIDER");
        var conta = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(conta.capture());
        assertThat(conta.getValue().getRole()).isEqualTo(UserRole.ROLE_PROVIDER);
        assertThat(conta.getValue().temPapel(UserRole.ROLE_CLIENT)).isTrue();
        verify(jwtService).generateAccessToken(any(), org.mockito.ArgumentMatchers.eq(UserRole.ROLE_PROVIDER));
        verify(backgroundCheckService).scheduleCheck(any());
        verify(termsAcceptanceRepository).save(any());
    }

    @Test
    void register_cpfNeverStoredAsPlainText() {
        cadastroPossivel();
        when(profileRepository.save(any())).thenAnswer(i -> {
            ProviderProfile p = i.getArgument(0);
            assertThat(p.getCpfCifrado()).doesNotContain("123.456.789-09");
            return p;
        });

        providerService.register(comCpf("123.456.789-09", "c2@test.com"), "203.0.113.5");
        verify(profileRepository).save(any());
    }

    // ── Uma pessoa = um CPF: o cadastro do prestador grava o hash (com a chave e a versão atuais) e recusa duplicata.

    @Test
    void register_gravaOHashDoCpf_naConta_eSoOHash() {
        cadastroPossivel();

        providerService.register(comCpf("111.444.777-35", "h@test.com"), "203.0.113.5");

        ArgumentCaptor<User> usuario = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(usuario.capture());
        assertThat(usuario.getValue().getCpfHash()).hasSize(64).doesNotContain("111").doesNotContain("444");
        assertThat(usuario.getValue().getCpfHash()).isEqualTo(cpfHashService.hash("11144477735"));
        assertThat(usuario.getValue().getCpfHashVersao()).isEqualTo(2);
    }

    @Test
    void register_cpfComEsemMascara_geraOMesmoHash_umaPessoaUmaIdentidade() {
        cadastroPossivel();
        providerService.register(comCpf("111.444.777-35", "m1@test.com"), "203.0.113.5");
        providerService.register(comCpf("11144477735", "m2@test.com"), "203.0.113.5");

        ArgumentCaptor<User> usuarios = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(usuarios.capture());
        assertThat(usuarios.getAllValues().get(0).getCpfHash()).isEqualTo(usuarios.getAllValues().get(1).getCpfHash());
    }

    @Test
    void register_cpfJaVinculadoAOutraConta_recusa_eNadaEhGravado() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByCpfHashIn(any())).thenReturn(true);

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
        verify(userRepository, never()).existsByCpfHashIn(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_emailJaCadastrado_orientaAEntrarENaContaExistente() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> providerService.register(
                new RegisterProviderRequest("X", "dup@test.com", "P@ss1", "000", "EL", null, true), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_IN_USE")
                .hasMessageContaining("Quero ser prestador");   // quem já é cliente vira prestador na MESMA conta
    }

    // ── Conta única com papéis: o cliente que passa a prestar serviço na mesma conta.

    private User clienteLogado() {
        User u = User.builder().nome("Ana").email("ana@test.com").senhaHash("$2a$x").role(UserRole.ROLE_CLIENT).build();
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    private BecomeProviderRequest tornar(String cpf) {
        return new BecomeProviderRequest(cpf, "ELETRICISTA", "Faço instalação elétrica", true);
    }

    @Test
    void tornarPrestador_acontaGanhaOPapel_criaPerfil_registraTermos_eAbreNoContextoDePrestador() {
        User ana = clienteLogado();
        when(userRepository.findByIdComTrava(ana.getId())).thenReturn(Optional.of(ana));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("tok");

        AuthResponse resp = providerService.tornarPrestador(ana.getId(), tornar("111.444.777-35"), "203.0.113.5");

        assertThat(ana.temPapel(UserRole.ROLE_PROVIDER)).isTrue();
        assertThat(ana.temPapel(UserRole.ROLE_CLIENT)).as("continua cliente").isTrue();
        assertThat(ana.getCpfHash()).isEqualTo(cpfHashService.hash("11144477735"));
        assertThat(resp.role()).isEqualTo("ROLE_PROVIDER");
        assertThat(resp.papeis()).containsExactly("ROLE_CLIENT", "ROLE_PROVIDER");
        verify(jwtService).generateAccessToken(ana, UserRole.ROLE_PROVIDER);
        var perfil = ArgumentCaptor.forClass(ProviderProfile.class);
        verify(profileRepository).save(perfil.capture());
        assertThat(perfil.getValue().getCpfCifrado()).isNotBlank().doesNotContain("11144477735").doesNotContain("111.444.777-35");
        assertThat(perfil.getValue().getBio()).isEqualTo("Faço instalação elétrica");
        verify(backgroundCheckService).scheduleCheck(any());   // entra EM_VERIFICACAO como qualquer prestador novo
        var termos = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository).save(termos.capture());
        assertThat(termos.getValue().getIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    void tornarPrestador_clienteQueJaConfirmouOCpf_precisaInformarOMesmo() {
        User ana = clienteLogado();
        ana.vincularCpf(cpfHashService.hash("11144477735"), 2);   // confirmou no 1º pagamento
        when(userRepository.findByIdComTrava(ana.getId())).thenReturn(Optional.of(ana));

        // outro CPF seria trocar de identidade depois de confirmada
        assertThatThrownBy(() -> providerService.tornarPrestador(ana.getId(), tornar("529.982.247-25"), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_MISMATCH");
        assertThat(ana.temPapel(UserRole.ROLE_PROVIDER)).isFalse();
        verify(profileRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void tornarPrestador_cpfDeOutraConta_recusa() {
        User ana = clienteLogado();
        when(userRepository.findByIdComTrava(ana.getId())).thenReturn(Optional.of(ana));
        when(userRepository.existsByCpfHashIn(any())).thenReturn(true);

        assertThatThrownBy(() -> providerService.tornarPrestador(ana.getId(), tornar("111.444.777-35"), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_ALREADY_REGISTERED");
        assertThat(ana.temPapel(UserRole.ROLE_PROVIDER)).isFalse();
        verify(profileRepository, never()).save(any());
    }

    @Test
    void tornarPrestador_quemJaEPrestador_recusa_semCriarSegundoPerfil() {
        User duda = User.builder().nome("Duda").email("d@test.com").senhaHash("$2a$x").role(UserRole.ROLE_PROVIDER).build();
        ReflectionTestUtils.setField(duda, "id", UUID.randomUUID());
        when(userRepository.findByIdComTrava(duda.getId())).thenReturn(Optional.of(duda));

        assertThatThrownBy(() -> providerService.tornarPrestador(duda.getId(), tornar("111.444.777-35"), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ALREADY_PROVIDER");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void tornarPrestador_admin_naoPodeViraPrestador() {
        User admin = User.builder().nome("Adm").email("a@test.com").senhaHash("$2a$x").role(UserRole.ROLE_ADMIN).build();
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        when(userRepository.findByIdComTrava(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> providerService.tornarPrestador(admin.getId(), tornar("111.444.777-35"), "203.0.113.5"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ROLE_NOT_AVAILABLE");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void tornarPrestador_leAContaComTravaDeLinha_otoqueDuploNaoCriaDoisPerfis() throws Exception {
        // a trava é o que serializa dois pedidos iguais: sem ela os dois leriam "ainda não é prestador" e criariam 2 perfis
        User ana = clienteLogado();
        when(userRepository.findByIdComTrava(ana.getId())).thenReturn(Optional.of(ana));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("tok");

        providerService.tornarPrestador(ana.getId(), tornar("111.444.777-35"), "203.0.113.5");

        verify(userRepository).findByIdComTrava(ana.getId());
        verify(userRepository, never()).findById(any());
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
