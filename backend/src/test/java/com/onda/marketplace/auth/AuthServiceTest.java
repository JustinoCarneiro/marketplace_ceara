package com.onda.marketplace.auth;

import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.shared.exception.PasswordMismatchException;
import com.onda.marketplace.shared.exception.TooManyAttemptsException;
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
    // Real: o que se prova aqui é a versão/rotação da chave, e um mock não a teria
    static final String CHAVE_ATUAL  = "test-cpf-hmac-key-0123456789-0123456789";
    static final String CHAVE_ANTIGA = "test-cpf-aes256-key-32-chars-here!";
    final CpfHashService cpfHashService = new CpfHashService(CHAVE_ATUAL, 2, CHAVE_ANTIGA);
    @Mock TermsAcceptanceRepository termsAcceptanceRepository;

    AuthService authService;

    @BeforeEach
    void setUp() {
        // PasswordAuthenticator REAL sobre os MESMOS mocks (ver PasswordAuthenticatorTest para a lógica isolada;
        // a transação própria, REQUIRES_NEW, só o E2E prova).
        var passwordAuthenticator = new PasswordAuthenticator(userRepository, passwordEncoder, new PasswordAttempts(5, 900));
        authService = new AuthService(
                userRepository, refreshTokenRepository, jwtService,
                passwordEncoder, cpfHashService, termsAcceptanceRepository,
                passwordAuthenticator, 30L);
    }

    @Test
    void registerClient_passwordNeverStoredAsPlainText() {
        var req = new RegisterClientRequest("Ana", "ana@example.com", "Senha@123", true);
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.registerClient(req, "203.0.113.5");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getSenhaHash())
                .as("Senha nunca deve ser armazenada em texto puro")
                .isNotEqualTo("Senha@123")
                .startsWith("$2a$");
    }

    @Test
    void registerClient_gravaProvaDeAceite() {
        // docs/PENDENCIAS_JURIDICAS.md item 3 — prova de consentimento informado.
        var req = new RegisterClientRequest("Ana", "ana@example.com", "Senha@123", true);
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.registerClient(req, "203.0.113.5");

        ArgumentCaptor<TermsAcceptance> captor = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository).save(captor.capture());
        TermsAcceptance saved = captor.getValue();
        assertThat(saved.getDocVersion()).isEqualTo(TermsAcceptance.CURRENT_DOC_VERSION);
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.5");
    }

    @Test
    void registerClient_duplicateEmail_throwsBusinessException() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                authService.registerClient(
                        new RegisterClientRequest("X", "dup@example.com", "Senha@123", true), "203.0.113.5"))
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
        when(userRepository.findByEmailComTrava("u@u.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.login(new LoginRequest("u@u.com", "errada")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
    }

    // ── US26: "suspender" tem que bloquear o acesso de verdade. O flag `ativo` era gravado pelo
    //    painel mas nunca consultado: o usuário suspenso continuava entrando e renovando sessão.

    private User usuarioSuspenso() {
        var user = User.builder()
                .email("s@s.com")
                .senhaHash("$2a$hash")
                .role(UserRole.ROLE_CLIENT)
                .build();
        user.suspender();
        return user;
    }

    @Test
    void login_contaSuspensa_comSenhaCorreta_ehBloqueada() {
        when(userRepository.findByEmailComTrava("s@s.com")).thenReturn(Optional.of(usuarioSuspenso()));
        when(passwordEncoder.matches("Senha@123", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("s@s.com", "Senha@123")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ACCOUNT_SUSPENDED");

        // nenhuma sessão nasce para a conta suspensa
        verify(jwtService, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_contaSuspensa_comSenhaErrada_naoRevelaQueEstaSuspensa() {
        // Quem não sabe a senha não descobre o estado da conta: a suspensão só aparece depois
        // das credenciais corretas, senão o login viraria um detector de contas suspensas.
        when(userRepository.findByEmailComTrava("s@s.com")).thenReturn(Optional.of(usuarioSuspenso()));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("s@s.com", "errada")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
    }

    @Test
    void login_contaReativada_voltaAEntrar() {
        User user = usuarioSuspenso();
        user.reativar();
        when(userRepository.findByEmailComTrava("s@s.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Senha@123", "$2a$hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(authService.login(new LoginRequest("s@s.com", "Senha@123")).accessToken())
                .isEqualTo("access");
    }


    // ── Limite de tentativas de senha: sem ele a senha de uma conta podia ser testada sem fim.

    private User usuarioComum() {
        return User.builder().email("l@l.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
    }

    private void errar(int vezes) {
        for (int i = 0; i < vezes; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("l@l.com", "errada")))
                    .isInstanceOf(PasswordMismatchException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        }
    }

    @Test
    void login_senhaErrada_contaOErro_naConta() {
        User user = usuarioComum();
        when(userRepository.findByEmailComTrava("l@l.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);

        errar(2);

        assertThat(user.getSenhaFalhas()).isEqualTo(2);
    }

    @Test
    void login_cincoErros_bloqueiam_ENemASenhaCertaEntraDuranteOBloqueio() {
        User user = usuarioComum();
        when(userRepository.findByEmailComTrava("l@l.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);
        errar(5);

        assertThatThrownBy(() -> authService.login(new LoginRequest("l@l.com", "Senha@123")))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasFieldOrPropertyWithValue("code", "TOO_MANY_ATTEMPTS");

        // bloqueada, a senha nem é conferida (e nenhuma sessão nasce): o BCrypt não vira oráculo de palpite
        verify(passwordEncoder, times(5)).matches(any(), any());
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void login_acerto_zeraOContador() {
        User user = usuarioComum();
        when(userRepository.findByEmailComTrava("l@l.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "$2a$hash")).thenReturn(false);
        when(passwordEncoder.matches("Senha@123", "$2a$hash")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        errar(4);

        authService.login(new LoginRequest("l@l.com", "Senha@123"));

        // senão 4 erros espalhados em semanas somariam e bloqueariam quem só errou digitando
        assertThat(user.getSenhaFalhas()).isZero();
    }

    @Test
    void login_emailDesconhecido_naoGastaContadorDeNinguem() {
        when(userRepository.findByEmailComTrava("x@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@x.com", "qualquer")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CREDENTIALS");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_delegaAContagemAUmaTransacaoPropriaQueSempreCommita() throws Exception {
        // Achado da revisão cruzada (2026-10-05): o acerto/erro de senha mora em PasswordAuthenticator, numa
        // transação PRÓPRIA (REQUIRES_NEW) — não mais dentro de login(). Antes, um acerto seguido de
        // ACCOUNT_SUSPENDED (que corretamente desfaz tudo o mais) desfazia também o PRÓPRIO acerto, na mesma
        // transação. Ver PasswordAuthenticatorTest para a verificação completa das anotações.
        var metodo = PasswordAuthenticator.class.getMethod("autenticarPorEmail", String.class, String.class);
        var tx = metodo.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        assertThat(tx.propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
        assertThat(tx.noRollbackFor()).containsExactlyInAnyOrder(PasswordMismatchException.class, TooManyAttemptsException.class);
    }

    @Test
    void refresh_contaSuspensa_naoRenovaASessao() {
        var token = new RefreshToken(usuarioSuspenso(), "hash", java.time.Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("qualquer")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");

        assertThat(token.isRevogado()).as("o token não é rotacionado nem reaproveitado").isFalse();
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void refresh_invalidToken_throwsBusinessException() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.refresh(new RefreshRequest("token-invalido")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_REFRESH_TOKEN");
    }

    // ── Conta única com papéis (antifraude Camada 3): o token carrega o papel EM USO; a conta pode ter mais de um.

    private User contaComDoisPapeis(UserRole principal) {
        User u = User.builder().nome("Duda").email("duda@x.com").senhaHash("$2a$hash").role(principal).build();
        u.concederPapel(UserRole.ROLE_CLIENT);
        u.concederPapel(UserRole.ROLE_PROVIDER);
        org.springframework.test.util.ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    private void sessaoPossivel() {
        when(jwtService.generateAccessToken(any(), any())).thenReturn("access");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registerClient_abreNoPapelDeCliente() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("$2a$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sessaoPossivel();

        var resposta = authService.registerClient(new RegisterClientRequest("Ana", "ana@example.com", "Senha@123", true), "203.0.113.5");

        verify(jwtService).generateAccessToken(any(), eq(UserRole.ROLE_CLIENT));
        assertThat(resposta.role()).isEqualTo("ROLE_CLIENT");
        assertThat(resposta.papeis()).containsExactly("ROLE_CLIENT");
    }

    @Test
    void login_abreNoPapelPrincipal_eListaTodosOsPapeisDaConta() {
        User duda = contaComDoisPapeis(UserRole.ROLE_PROVIDER);
        when(userRepository.findByEmailComTrava("duda@x.com")).thenReturn(Optional.of(duda));
        when(passwordEncoder.matches("Senha@123", "$2a$hash")).thenReturn(true);
        sessaoPossivel();

        var resposta = authService.login(new LoginRequest("duda@x.com", "Senha@123"));

        verify(jwtService).generateAccessToken(duda, UserRole.ROLE_PROVIDER);
        assertThat(resposta.role()).isEqualTo("ROLE_PROVIDER");
        assertThat(resposta.papeis()).containsExactly("ROLE_CLIENT", "ROLE_PROVIDER");   // para o app oferecer "alternar"
        var rt = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(rt.capture());
        assertThat(rt.getValue().getPapel()).as("o refresh token guarda o contexto da sessão").isEqualTo(UserRole.ROLE_PROVIDER);
    }

    @Test
    void refresh_mantemOContextoDaSessao_naoVoltaAoPapelPrincipal() {
        // quem trocou para prestador e renova a sessão continua prestador: sem isto, cada renovação (15 min) derrubaria o
        // usuário de volta ao modo cliente no meio de um atendimento
        User duda = contaComDoisPapeis(UserRole.ROLE_CLIENT);
        var token = new RefreshToken(duda, "hash", java.time.Instant.now().plusSeconds(3600), UserRole.ROLE_PROVIDER);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        sessaoPossivel();

        var resposta = authService.refresh(new RefreshRequest("qualquer"));

        verify(jwtService).generateAccessToken(duda, UserRole.ROLE_PROVIDER);
        assertThat(resposta.role()).isEqualTo("ROLE_PROVIDER");
        assertThat(token.isRevogado()).isTrue();
    }

    @Test
    void refresh_sessaoAnteriorAMigracao_semContexto_renovaNoPapelPrincipal() {
        User duda = contaComDoisPapeis(UserRole.ROLE_CLIENT);
        var token = new RefreshToken(duda, "hash", java.time.Instant.now().plusSeconds(3600));   // papel null
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        sessaoPossivel();

        authService.refresh(new RefreshRequest("qualquer"));

        verify(jwtService).generateAccessToken(duda, UserRole.ROLE_CLIENT);
    }

    @Test
    void refresh_contextoQueAContaNaoTemMais_voltaAoPrincipal() {
        User soCliente = User.builder().nome("Ana").email("ana@x.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        var token = new RefreshToken(soCliente, "hash", java.time.Instant.now().plusSeconds(3600), UserRole.ROLE_PROVIDER);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        sessaoPossivel();

        authService.refresh(new RefreshRequest("qualquer"));

        verify(jwtService).generateAccessToken(soCliente, UserRole.ROLE_CLIENT);   // nunca um papel que a conta não tem
    }

    @Test
    void switchRole_paraUmPapelQueAContaTem_emiteNovoContexto_eRevogaASessaoAnterior() {
        User duda = contaComDoisPapeis(UserRole.ROLE_PROVIDER);
        var anterior = new RefreshToken(duda, "x", java.time.Instant.now().plusSeconds(3600), UserRole.ROLE_PROVIDER);
        when(userRepository.findById(duda.getId())).thenReturn(Optional.of(duda));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(anterior));
        sessaoPossivel();

        var resposta = authService.switchRole(duda.getId(), new SwitchRoleRequest("ROLE_CLIENT", "refresh-anterior"));

        verify(jwtService).generateAccessToken(duda, UserRole.ROLE_CLIENT);
        assertThat(resposta.role()).isEqualTo("ROLE_CLIENT");
        assertThat(resposta.papeis()).containsExactly("ROLE_CLIENT", "ROLE_PROVIDER");
        assertThat(anterior.isRevogado()).as("a sessão anterior não fica valendo no papel velho").isTrue();
    }

    @Test
    void switchRole_naoRevogaOTokenDeOutraConta() {
        User duda = contaComDoisPapeis(UserRole.ROLE_PROVIDER);
        User outra = contaComDoisPapeis(UserRole.ROLE_CLIENT);
        var tokenDeOutra = new RefreshToken(outra, "x", java.time.Instant.now().plusSeconds(3600), UserRole.ROLE_CLIENT);
        when(userRepository.findById(duda.getId())).thenReturn(Optional.of(duda));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenDeOutra));
        sessaoPossivel();

        authService.switchRole(duda.getId(), new SwitchRoleRequest("ROLE_CLIENT", "refresh-de-outra-conta"));

        assertThat(tokenDeOutra.isRevogado()).isFalse();
    }

    @Test
    void switchRole_paraPapelQueAContaNaoTem_recusaSemEmitirNada() {
        User soCliente = User.builder().nome("Ana").email("ana@x.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
        org.springframework.test.util.ReflectionTestUtils.setField(soCliente, "id", UUID.randomUUID());
        when(userRepository.findById(soCliente.getId())).thenReturn(Optional.of(soCliente));

        assertThatThrownBy(() -> authService.switchRole(soCliente.getId(), new SwitchRoleRequest("ROLE_PROVIDER", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ROLE_NOT_AVAILABLE")
                .hasMessageContaining("Cadastre-se como prestador");
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    @Test
    void switchRole_nuncaViraAdmin_nemComPapelInvalido() {
        User duda = contaComDoisPapeis(UserRole.ROLE_CLIENT);
        duda.concederPapel(UserRole.ROLE_ADMIN);   // mesmo que algo gravasse isso, a troca não entrega um token de admin
        when(userRepository.findById(duda.getId())).thenReturn(Optional.of(duda));

        assertThatThrownBy(() -> authService.switchRole(duda.getId(), new SwitchRoleRequest("ROLE_ADMIN", null)))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", "ROLE_NOT_AVAILABLE");
        assertThatThrownBy(() -> authService.switchRole(duda.getId(), new SwitchRoleRequest("ROLE_DONO_DO_MUNDO", null)))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", "INVALID_ROLE");
        verify(jwtService, never()).generateAccessToken(any(), any());
    }

    // Antifraude Camada 2 (PENDENCIAS_INTEGRIDADE.md): CPF único na plataforma — sem isto,
    // a mesma pessoa cria uma segunda conta pra se auto-contratar e fabricar reputação.

    private static final String CPF = "11144477735";

    private User semCpf() {
        return User.builder().email("u@u.com").senhaHash("$2a$hash").role(UserRole.ROLE_CLIENT).build();
    }

    private User comHashDaChaveAntiga(String cpf) {
        User u = semCpf();
        // calculado com a chave ANTIGA, na versão 1 — como as contas de antes da separação das chaves
        u.vincularCpf(new CpfHashService(CHAVE_ANTIGA, 1).hash(cpf), 1);
        return u;
    }

    @Test
    void verifyIdentity_cpfNovo_vinculaOHashDaChaveAtual_comAVersaoAtual() {
        UUID userId = UUID.randomUUID();
        var user = semCpf();
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.of(user));

        authService.verifyIdentity(CPF, userId);

        assertThat(user.getCpfHash()).isEqualTo(cpfHashService.hash(CPF)).hasSize(64);
        assertThat(user.getCpfHashVersao()).isEqualTo(2);
        verify(userRepository).save(user);
    }

    @Test
    void verifyIdentity_cpfJaVinculadoAOutraConta_lancaCpfAlreadyRegistered_consultandoAsDuasChaves() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.of(semCpf()));
        when(userRepository.existsByCpfHashIn(any())).thenReturn(true);

        assertThatThrownBy(() -> authService.verifyIdentity(CPF, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_ALREADY_REGISTERED")
                .hasMessageContaining("fale com o suporte");
        verify(userRepository, never()).save(any());

        // numa rotação o dono pode estar sob a chave antiga: os DOIS hashes são consultados
        @SuppressWarnings("unchecked") ArgumentCaptor<java.util.Collection<String>> hashes = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(userRepository).existsByCpfHashIn(hashes.capture());
        assertThat(hashes.getValue()).containsExactlyInAnyOrder(
                cpfHashService.hash(CPF), new CpfHashService(CHAVE_ANTIGA, 1).hash(CPF));
    }

    @Test
    void verifyIdentity_retryComMesmoCpfJaVerificado_naoLancaEhIdempotente() {
        // Regressão: antes, checar a duplicata ANTES do hash do próprio usuário fazia um retry com o mesmo CPF colidir com o
        // próprio registro e vazar CPF_ALREADY_REGISTERED.
        UUID userId = UUID.randomUUID();
        var user = semCpf();
        user.vincularCpf(cpfHashService.hash(CPF), 2);
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.of(user));

        assertThatCode(() -> authService.verifyIdentity(CPF, userId)).doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByCpfHashIn(any());
    }

    @Test
    void verifyIdentity_cpfDiferenteDoJaConfirmado_recusa_trocarOCpfBurlariaABanimentoEAUnicidade() {
        UUID userId = UUID.randomUUID();
        var user = semCpf();
        user.vincularCpf(cpfHashService.hash(CPF), 2);
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyIdentity("52998224725", userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_MISMATCH");
        assertThat(user.getCpfHash()).isEqualTo(cpfHashService.hash(CPF));
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyIdentity_hashDeChaveAntiga_mesmoCpf_regravaComAChaveAtual() {
        // o cliente que confirmou o CPF antes da separação das chaves: na próxima confirmação o hash dele migra
        UUID userId = UUID.randomUUID();
        var user = comHashDaChaveAntiga(CPF);
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.of(user));

        authService.verifyIdentity(CPF, userId);

        assertThat(user.getCpfHash()).isEqualTo(cpfHashService.hash(CPF));
        assertThat(user.getCpfHashVersao()).isEqualTo(2);
        verify(userRepository).save(user);
        verify(userRepository, never()).existsByCpfHashIn(any());   // é a mesma conta: não é duplicata dela mesma
    }

    @Test
    void verifyIdentity_hashDeChaveAntiga_outroCpf_recusa() {
        UUID userId = UUID.randomUUID();
        var user = comHashDaChaveAntiga(CPF);
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyIdentity("52998224725", userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "CPF_MISMATCH");
        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyIdentity_cpfInvalido_recusa_semConsultarNemGravar() {
        // sem validar os dígitos, um número inventado burlaria a unicidade (o hash seria "único" por ser falso)
        UUID userId = UUID.randomUUID();

        for (String invalido : new String[] {"11122233344", "123.456.789-00", "111.111.111-11"}) {
            assertThatThrownBy(() -> authService.verifyIdentity(invalido, userId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_CPF");
        }
        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyIdentity_contaInexistente_recusa() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdComTrava(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyIdentity(CPF, userId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "USER_NOT_FOUND");
    }
}
