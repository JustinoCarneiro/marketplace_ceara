package com.onda.marketplace.auth;

import com.onda.marketplace.notification.UserMailSender;
import com.onda.marketplace.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Recuperação de senha por código enviado ao e-mail (US35).
 *
 * <p>Decisões de segurança (OWASP Forgot Password):
 * <ul>
 *   <li><b>Não revela se o e-mail existe</b>: {@link #solicitar} responde igual para e-mail
 *       desconhecido, conta suspensa e limite excedido; o e-mail sai depois do commit, em outra
 *       thread ({@link PasswordResetMailListener}), e {@link #redefinir} dá a mesma mensagem para
 *       qualquer falha do código.</li>
 *   <li><b>Código fora do banco e do log</b>: 8 caracteres de um alfabeto sem ambiguidade (40
 *       bits) vindos de {@link SecureRandom}; só o HMAC-SHA256 é gravado, com o usuário no
 *       cálculo.</li>
 *   <li><b>Força bruta limitada</b>: 5 erros fecham o código, validade de 30 min, uso único e
 *       um pedido novo invalida o anterior. A consulta que confere o código trava a linha, então
 *       palpites simultâneos não furam o contador.</li>
 *   <li><b>Sem inundar caixa alheia</b>: no máximo 3 códigos por hora por usuário.</li>
 *   <li><b>Troca encerra as sessões</b>: todos os refresh tokens são revogados.</li>
 * </ul>
 */
@Service
@SuppressWarnings("null")
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Crockford base32: 32 símbolos, sem I, L, O, U (confundem com 1, 0 e V). 32^8 = 2^40. */
    private static final char[] ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int    TAMANHO  = 8;
    /** BCrypt só considera os 72 primeiros bytes da senha. */
    private static final int    MAX_BYTES_SENHA = 72;
    private static final String DOMINIO_HMAC = "pwd-reset:v1|";

    private static final UUID USUARIO_INEXISTENTE = new UUID(0L, 0L);

    private final UserRepository              userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final RefreshTokenRepository      refreshTokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final ApplicationEventPublisher   events;
    private final UserMailSender              mailSender;
    private final byte[]                      chaveHmac;
    private final long                        validadeMinutos;
    private final int                         maxPorHora;
    private final int                         maxTentativas;
    private final SecureRandom                random = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetCodeRepository codeRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events,
            UserMailSender mailSender,
            @Value("${jwt.secret}") String chave,
            @Value("${marketplace.password-reset.validade-minutos:30}") long validadeMinutos,
            @Value("${marketplace.password-reset.max-por-hora:3}") int maxPorHora,
            @Value("${marketplace.password-reset.max-tentativas:5}") int maxTentativas) {
        this.userRepository         = userRepository;
        this.codeRepository         = codeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder        = passwordEncoder;
        this.events                 = events;
        this.mailSender             = mailSender;
        this.chaveHmac              = chave.getBytes(StandardCharsets.UTF_8);
        this.validadeMinutos        = validadeMinutos;
        this.maxPorHora             = maxPorHora;
        this.maxTentativas          = maxTentativas;
    }

    /**
     * Pede um código. Não devolve nada e não lança para e-mail desconhecido, conta suspensa ou
     * limite excedido — o chamador responde sempre a mesma coisa.
     *
     * @throws BusinessException {@code PASSWORD_RESET_UNAVAILABLE} se o servidor não tem canal de
     *         e-mail. É um estado global (não depende do e-mail informado), então dizer a verdade
     *         não vaza nada e evita prometer um e-mail que nunca vai sair.
     */
    @Transactional
    public void solicitar(String email) {
        if (!mailSender.ativo()) {
            throw new BusinessException("PASSWORD_RESET_UNAVAILABLE",
                    "A recuperação de senha está indisponível no momento. Fale com o suporte em suporte@onda.app.");
        }
        Optional<User> alvo = userRepository.findByEmail(email).filter(User::isAtivo);
        if (alvo.isEmpty()) {
            return;
        }
        User user = alvo.get();
        Instant agora = Instant.now();

        if (codeRepository.countByUserIdAndCreatedAtAfter(user.getId(), agora.minus(Duration.ofHours(1))) >= maxPorHora) {
            log.info("Recuperação de senha: limite por hora atingido (userId={})", user.getId());
            return;
        }

        codeRepository.fecharAtivosDoUsuario(user.getId(), agora);   // só o último código vale
        String codigo = gerarCodigo();
        codeRepository.save(new PasswordResetCode(user.getId(), hash(user.getId(), codigo),
                agora.plus(Duration.ofMinutes(validadeMinutos))));
        events.publishEvent(new PasswordResetRequested(user.getEmail(), user.getNome(), codigo, validadeMinutos));
    }

    /**
     * Troca a senha com o código recebido por e-mail.
     *
     * <p>{@code noRollbackFor}: o erro de código precisa PERSISTIR a tentativa. Sem isso, a
     * exceção desfaria a transação inteira e o contador nunca subiria — o limite de 5 não valeria
     * nada. Teste mockado não pega isso; o E2E contra o Postgres sim.
     *
     * @throws BusinessException {@code INVALID_RESET_CODE} (sem distinguir expirado, errado, já
     *         usado, e-mail desconhecido ou conta suspensa) ou {@code INVALID_PASSWORD}
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void redefinir(String email, String codigoDigitado, String novaSenha) {
        // Antes de tocar no código: senha que o BCrypt truncaria em silêncio não gasta tentativa.
        if (novaSenha.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_SENHA) {
            throw new BusinessException("INVALID_PASSWORD", "A senha é longa demais (máximo de 72 bytes).");
        }
        Instant agora = Instant.now();
        Optional<User> alvo = userRepository.findByEmail(email).filter(User::isAtivo);

        // Calcula o HMAC mesmo sem usuário: o tempo não deve separar e-mail inexistente de código errado.
        String digitado = hash(alvo.map(User::getId).orElse(USUARIO_INEXISTENTE), normalizar(codigoDigitado));
        if (alvo.isEmpty()) {
            throw codigoInvalido();
        }
        User user = alvo.get();

        PasswordResetCode codigo = codeRepository.ativosDoUsuarioComTrava(user.getId(), agora).stream()
                .findFirst()
                .filter(c -> c.isAtivo(agora, maxTentativas))
                .orElseThrow(PasswordResetService::codigoInvalido);

        if (!MessageDigest.isEqual(digitado.getBytes(StandardCharsets.UTF_8),
                codigo.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            codigo.registrarErro(maxTentativas, agora);
            codeRepository.save(codigo);
            throw codigoInvalido();
        }

        user.trocarSenha(passwordEncoder.encode(novaSenha));
        userRepository.save(user);
        codigo.fechar(agora);
        codeRepository.save(codigo);
        refreshTokenRepository.revogarTodosDoUsuario(user.getId());
        events.publishEvent(new PasswordChanged(user.getEmail(), user.getNome()));
    }

    private static BusinessException codigoInvalido() {
        return new BusinessException("INVALID_RESET_CODE", "Código inválido ou expirado.");
    }

    private String gerarCodigo() {
        char[] c = new char[TAMANHO];
        for (int i = 0; i < TAMANHO; i++) {
            c[i] = ALFABETO[random.nextInt(ALFABETO.length)];   // 32 símbolos: sorteio sem viés
        }
        return new String(c);
    }

    /**
     * Aceita o código como o usuário digita: minúsculas, hífen de leitura, espaços e as confusões
     * clássicas (a letra O no lugar do zero, I ou L no lugar do 1).
     */
    private static String normalizar(String digitado) {
        StringBuilder sb = new StringBuilder();
        for (char ch : digitado.toUpperCase().toCharArray()) {
            if (!Character.isLetterOrDigit(ch)) continue;
            sb.append(switch (ch) {
                case 'O'      -> '0';
                case 'I', 'L' -> '1';
                default       -> ch;
            });
        }
        return sb.toString();
    }

    /** HMAC-SHA256 do código com o usuário no cálculo (o mesmo código em outra conta dá outro hash). */
    private String hash(UUID userId, String codigoNormalizado) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chaveHmac, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (DOMINIO_HMAC + userId + "|" + codigoNormalizado).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao calcular o HMAC do código de recuperação", e);
        }
    }
}
