package com.onda.marketplace.auth;

import com.onda.marketplace.notification.UserMailSender;
import com.onda.marketplace.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * US35 — recuperação de senha. Os testes fixam o contrato de SEGURANÇA, não só o caminho feliz:
 * o código nunca fica em claro, a resposta não revela se o e-mail existe, tentativas e pedidos
 * são limitados, a troca encerra as sessões e as mensagens de erro não distinguem o motivo.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PasswordResetServiceTest {

    @Mock UserRepository              userRepository;
    @Mock PasswordResetCodeRepository codeRepository;
    @Mock RefreshTokenRepository      refreshTokenRepository;
    @Mock PasswordEncoder             passwordEncoder;
    @Mock ApplicationEventPublisher   events;
    @Mock UserMailSender              mailSender;

    PasswordResetService service;

    private static final String CHAVE = "chave-hmac-de-teste-com-mais-de-32-caracteres!";
    private static final UUID   USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, codeRepository, refreshTokenRepository,
                passwordEncoder, events, mailSender, CHAVE, 30, 3, 5);
        lenient().when(mailSender.ativo()).thenReturn(true);
    }

    private User usuario(boolean ativo) {
        User u = User.builder().nome("Ana").email("ana@example.com")
                .senhaHash("$2a$antigo").role(UserRole.ROLE_CLIENT).build();
        ReflectionTestUtils.setField(u, "id", USER_ID);
        if (!ativo) u.suspender();
        return u;
    }

    /** O HMAC calculado de forma independente do serviço: confere o que de fato foi gravado. */
    private static String hmac(UUID userId, String codigoNormalizado) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(CHAVE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(
                ("pwd-reset:v1|" + userId + "|" + codigoNormalizado).getBytes(StandardCharsets.UTF_8)));
    }

    private PasswordResetCode codigoAtivo(String codigo) throws Exception {
        return new PasswordResetCode(USER_ID, hmac(USER_ID, codigo), Instant.now().plus(Duration.ofMinutes(20)));
    }

    // ───────────────────────────── solicitar ─────────────────────────────

    @Test
    void solicitar_emailDesconhecido_naoFazNadaENaoLanca() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());

        // a resposta é a mesma de um e-mail cadastrado: nada de exceção que denuncie a diferença
        assertThatCode(() -> service.solicitar("ninguem@example.com")).doesNotThrowAnyException();

        verifyNoInteractions(codeRepository, events);
    }

    @Test
    void solicitar_contaSuspensa_naoEmiteCodigo() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(false)));

        assertThatCode(() -> service.solicitar("ana@example.com")).doesNotThrowAnyException();

        verifyNoInteractions(codeRepository, events);
    }

    @Test
    void solicitar_contaAtiva_gravaSoOHash_invalidaOAnterior_ePublicaOCodigoNoEvento() throws Exception {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(0L);

        service.solicitar("ana@example.com");

        ArgumentCaptor<PasswordResetCode> salvo = ArgumentCaptor.forClass(PasswordResetCode.class);
        ArgumentCaptor<PasswordResetRequested> evento = ArgumentCaptor.forClass(PasswordResetRequested.class);
        InOrder ordem = inOrder(codeRepository, events);
        ordem.verify(codeRepository).fecharAtivosDoUsuario(eq(USER_ID), any());   // um pedido novo invalida o anterior
        ordem.verify(codeRepository).save(salvo.capture());
        ordem.verify(events).publishEvent(evento.capture());

        String codigo = evento.getValue().codigo();
        assertThat(codigo).matches("[0-9A-HJKMNP-TV-Z]{8}");
        // o que vai pro banco é o HMAC (com o usuário no cálculo), nunca o código
        assertThat(salvo.getValue().getCodeHash()).isEqualTo(hmac(USER_ID, codigo)).doesNotContain(codigo);
        assertThat(salvo.getValue().getExpiresAt())
                .isBetween(Instant.now().plus(Duration.ofMinutes(29)), Instant.now().plus(Duration.ofMinutes(31)));
        assertThat(evento.getValue().email()).isEqualTo("ana@example.com");
        assertThat(evento.getValue().validadeMinutos()).isEqualTo(30);
    }

    @Test
    void solicitar_geraCodigosAleatoriosDoAlfabetoSemCaracteresAmbiguos() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(0L);

        for (int i = 0; i < 200; i++) service.solicitar("ana@example.com");

        ArgumentCaptor<PasswordResetRequested> eventos = ArgumentCaptor.forClass(PasswordResetRequested.class);
        verify(events, times(200)).publishEvent(eventos.capture());
        Set<String> distintos = new HashSet<>();
        for (PasswordResetRequested e : eventos.getAllValues()) {
            // sem I, L, O, U (confundem com 1/0/V) e com 8 posições = 40 bits
            assertThat(e.codigo()).matches("[0-9A-HJKMNP-TV-Z]{8}");
            distintos.add(e.codigo());
        }
        assertThat(distintos).as("200 códigos iguais seriam um gerador quebrado").hasSizeGreaterThan(190);
    }

    @Test
    void solicitar_tresPedidosNaUltimaHora_oQuartoNaoEnviaNada() {
        // proteção contra inundar a caixa de entrada de terceiros: a resposta é a mesma, mas nada sai
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.countByUserIdAndCreatedAtAfter(eq(USER_ID), any())).thenReturn(3L);

        assertThatCode(() -> service.solicitar("ana@example.com")).doesNotThrowAnyException();

        verify(codeRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void solicitar_semEmailConfigurado_avisaIndisponivelSemTocarNoBanco() {
        // Estado GLOBAL (não depende do e-mail informado), então dizer a verdade não vaza nada:
        // melhor que prometer um e-mail que nunca vai sair.
        when(mailSender.ativo()).thenReturn(false);

        assertThatThrownBy(() -> service.solicitar("ana@example.com"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "PASSWORD_RESET_UNAVAILABLE");

        verifyNoInteractions(userRepository, codeRepository, events);
    }

    // ───────────────────────────── redefinir ─────────────────────────────

    @Test
    void redefinir_codigoCorreto_trocaSenha_fechaCodigo_encerraSessoes_eAvisa() throws Exception {
        User user = usuario(true);
        PasswordResetCode codigo = codigoAtivo("ABCD2345");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(codigo));
        when(passwordEncoder.encode("NovaSenha@1")).thenReturn("$2a$novo");

        service.redefinir("ana@example.com", "ABCD2345", "NovaSenha@1");

        assertThat(user.getSenhaHash()).isEqualTo("$2a$novo").isNotEqualTo("NovaSenha@1");
        assertThat(codigo.getClosedAt()).as("o código é de uso único").isNotNull();
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revogarTodosDoUsuario(USER_ID);   // quem tinha a senha antiga é deslogado
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOf(PasswordChanged.class);
    }

    @Test
    void redefinir_codigoCorreto_tiraOBloqueioPorTentativasDeSenha_eEhASaidaDeQuemFoiBloqueado() throws Exception {
        // Quem foi bloqueado por erros de senha (às vezes por culpa de outra pessoa) tem uma saída que não depende de
        // esperar: a recuperação pelo e-mail prova a posse da conta e zera o limite.
        User user = usuario(true);
        for (int i = 0; i < 5; i++) user.registrarSenhaErrada(Instant.now(), 5, Duration.ofMinutes(15));
        assertThat(user.senhaBloqueada(Instant.now())).isTrue();
        PasswordResetCode codigo = codigoAtivo("ABCD2345");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(codigo));
        when(passwordEncoder.encode("NovaSenha@1")).thenReturn("$2a$novo");

        service.redefinir("ana@example.com", "ABCD2345", "NovaSenha@1");

        assertThat(user.senhaBloqueada(Instant.now())).isFalse();
        assertThat(user.getSenhaFalhas()).isZero();
    }

    @Test
    void redefinir_aceitaOCodigoComoOUsuarioDigita_minusculoComHifenEOTrocadoPorZero() throws Exception {
        PasswordResetCode codigo = codigoAtivo("0BCD2345");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(codigo));
        when(passwordEncoder.encode(any())).thenReturn("$2a$novo");

        // "obcd-2345": minúsculas, hífen de leitura e a letra O no lugar do zero
        assertThatCode(() -> service.redefinir("ana@example.com", " obcd-2345 ", "NovaSenha@1"))
                .doesNotThrowAnyException();
        assertThat(codigo.getClosedAt()).isNotNull();
    }

    @Test
    void redefinir_codigoErrado_contaATentativa_eNaoTrocaASenha() throws Exception {
        User user = usuario(true);
        PasswordResetCode codigo = codigoAtivo("ABCD2345");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(codigo));

        assertThatThrownBy(() -> service.redefinir("ana@example.com", "ZZZZ9999", "NovaSenha@1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_RESET_CODE");

        assertThat(codigo.getAttempts()).isEqualTo(1);
        assertThat(codigo.getClosedAt()).as("1 erro não fecha o código").isNull();
        assertThat(user.getSenhaHash()).isEqualTo("$2a$antigo");
        verify(codeRepository).save(codigo);                  // a tentativa precisa ser persistida
        verify(refreshTokenRepository, never()).revogarTodosDoUsuario(any());
        verifyNoInteractions(events);
    }

    @Test
    void redefinir_quintaTentativaErrada_fechaOCodigo_eOCertoNaoServeMais() throws Exception {
        PasswordResetCode codigo = codigoAtivo("ABCD2345");
        for (int i = 0; i < 4; i++) codigo.registrarErro(5, Instant.now());   // já errou 4 vezes
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(codigo));

        assertThatThrownBy(() -> service.redefinir("ana@example.com", "ZZZZ9999", "NovaSenha@1"))
                .isInstanceOf(BusinessException.class);

        assertThat(codigo.getAttempts()).isEqualTo(5);
        assertThat(codigo.getClosedAt()).as("5 erros invalidam o código: força pedir outro").isNotNull();
    }

    @Test
    void redefinir_semCodigoAtivo_expiradoOuJaUsado_dizSoQueEInvalido() {
        // a consulta só devolve códigos abertos e dentro da validade: lista vazia cobre expirado,
        // já usado, fechado por excesso de tentativas e "nunca pediu" — sem distinguir o motivo
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.redefinir("ana@example.com", "ABCD2345", "NovaSenha@1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_RESET_CODE")
                .hasMessage("Código inválido ou expirado.");
    }

    @Test
    void redefinir_codigoExpiradoQueEscapouDaConsulta_tambemEhRecusado() throws Exception {
        PasswordResetCode expirado = new PasswordResetCode(USER_ID, hmac(USER_ID, "ABCD2345"),
                Instant.now().minus(Duration.ofMinutes(1)));
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(true)));
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(expirado));

        assertThatThrownBy(() -> service.redefinir("ana@example.com", "ABCD2345", "NovaSenha@1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_RESET_CODE");
        verify(userRepository, never()).save(any());
    }

    @Test
    void redefinir_emailDesconhecido_eContaSuspensa_respondemExatamenteIgual() {
        when(userRepository.findByEmail("ninguem@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(usuario(false)));

        for (String email : List.of("ninguem@example.com", "ana@example.com")) {
            assertThatThrownBy(() -> service.redefinir(email, "ABCD2345", "NovaSenha@1"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_RESET_CODE")
                    .hasMessage("Código inválido ou expirado.");
        }
        verifyNoInteractions(codeRepository, refreshTokenRepository, events);
    }

    @Test
    void redefinir_senhaLongaDemaisParaOBcrypt_ehRecusadaSemGastarTentativa() {
        // BCrypt só usa 72 bytes: acima disso o resto seria ignorado em silêncio (ou estouraria).
        // 40 "ç" = 80 bytes em UTF-8, mesmo com só 40 caracteres.
        String longa = "ç".repeat(40);

        assertThatThrownBy(() -> service.redefinir("ana@example.com", "ABCD2345", longa))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PASSWORD");

        verifyNoInteractions(codeRepository, userRepository);   // nem consultou: o código não é consumido
    }

    // ─────────────────────────── ida e volta, sem hash de teste ───────────────────────────

    @Test
    void idaEVolta_oCodigoQueSaiNoEmailAbreAReDefinicao() {
        // O que solicitar() grava é aceito por redefinir() quando o usuário digita o código do e-mail.
        User user = usuario(true);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(codeRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("$2a$novo");

        service.solicitar("ana@example.com");

        ArgumentCaptor<PasswordResetCode> salvo = ArgumentCaptor.forClass(PasswordResetCode.class);
        ArgumentCaptor<PasswordResetRequested> evento = ArgumentCaptor.forClass(PasswordResetRequested.class);
        verify(codeRepository).save(salvo.capture());
        verify(events).publishEvent(evento.capture());
        when(codeRepository.ativosDoUsuarioComTrava(eq(USER_ID), any())).thenReturn(List.of(salvo.getValue()));

        service.redefinir("ana@example.com", evento.getValue().codigo(), "NovaSenha@1");

        assertThat(user.getSenhaHash()).isEqualTo("$2a$novo");
    }

    @Test
    void oEventoNuncaImprimeOCodigoNemOEmail() {
        // se alguém logar o evento por descuido, o código não pode sair junto
        var evento = new PasswordResetRequested("ana@example.com", "Ana", "ABCD2345", 30);

        assertThat(evento.toString()).doesNotContain("ABCD2345").doesNotContain("ana@example.com");
    }
}
