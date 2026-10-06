package com.onda.marketplace.auth;

import com.onda.marketplace.shared.Cpf;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@SuppressWarnings("null")
public class AuthService {

    private final UserRepository            userRepository;
    private final RefreshTokenRepository    refreshTokenRepository;
    private final JwtService                jwtService;
    private final PasswordEncoder           passwordEncoder;
    private final CpfHashService            cpfHashService;
    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final PasswordAuthenticator     passwordAuthenticator;
    private final long                      refreshTokenDays;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            CpfHashService cpfHashService,
            TermsAcceptanceRepository termsAcceptanceRepository,
            PasswordAuthenticator passwordAuthenticator,
            @Value("${jwt.refresh-token-days:30}") long refreshTokenDays) {
        this.userRepository            = userRepository;
        this.refreshTokenRepository    = refreshTokenRepository;
        this.jwtService                = jwtService;
        this.passwordEncoder           = passwordEncoder;
        this.cpfHashService            = cpfHashService;
        this.passwordAuthenticator     = passwordAuthenticator;
        this.termsAcceptanceRepository = termsAcceptanceRepository;
        this.refreshTokenDays          = refreshTokenDays;
    }

    /**
     * Confirma a identidade da conta pelo CPF (1º pagamento de quem ainda não tem CPF vinculado). Só o hash é guardado (LGPD).
     * Garante "uma pessoa = um CPF" na plataforma inteira; a conta única com papéis (V24) é o que deixa o prestador
     * contratar sem uma segunda conta.
     *
     * <p>Com trava de linha (achado da revisão cruzada, 2026-10-05 — a mesma trava já usada por {@code become-provider}
     * e por {@code AccountDeletionService.excluir}): sem ela, confirmar a identidade e criar o perfil de prestador ao
     * mesmo tempo liam o mesmo estado inicial (CPF ainda vazio); a última escrita vence, e o hash da conta podia
     * divergir do CPF cifrado do perfil, contornando {@code CPF_MISMATCH}. Contra uma exclusão em voo, a mesma trava
     * evita o lost update que desfaria a anonimização (UPDATE não é por coluna).
     */
    @Transactional
    public void verifyIdentity(String cpf, UUID userId) {
        exigirCpfValido(cpf);   // antes de qualquer consulta: CPF inventado nem chega ao banco
        User user = userRepository.findByIdComTrava(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));
        if (vincularCpf(user, cpf)) {
            userRepository.save(user);
        }
    }

    /**
     * Vincula o CPF (só o hash) à conta. Quem chama persiste: é usado também no cadastro, com a conta ainda sem id.
     *
     * <ul>
     *   <li>CPF com dígito errado: {@code INVALID_CPF} (sem validar, um número inventado burlaria a unicidade);</li>
     *   <li>a conta já tem CPF e é o MESMO: idempotente; se o hash está numa chave antiga, é regravado com a atual;</li>
     *   <li>a conta já tem CPF e é OUTRO: {@code CPF_MISMATCH} — trocar o CPF depois de confirmado burlaria o banimento e a
     *       unicidade;</li>
     *   <li>o CPF já é de outra conta (sob qualquer chave): {@code CPF_ALREADY_REGISTERED}.</li>
     * </ul>
     *
     * @return {@code true} se a conta mudou (o chamador precisa persistir)
     */
    public boolean vincularCpf(User user, String cpf) {
        exigirCpfValido(cpf);
        if (user.getCpfHash() != null) {
            // Idempotente de verdade: o hash do próprio usuário é conferido ANTES da consulta global, senão um retry com o
            // mesmo CPF colidiria com o próprio registro e viraria CPF_ALREADY_REGISTERED ("outra conta" sendo a dele).
            if (!cpfHashService.confere(cpf, user.getCpfHash(), user.getCpfHashVersao())) {
                throw new BusinessException("CPF_MISMATCH",
                        "Este CPF é diferente do que já foi confirmado nesta conta. Se o confirmado estiver errado, "
                                + "fale com o suporte.");
            }
            if (user.getCpfHashVersao() == cpfHashService.versaoAtual()) {
                return false;
            }
            user.vincularCpf(cpfHashService.hash(cpf), cpfHashService.versaoAtual());   // hash de chave antiga: regrava
            return true;
        }
        if (userRepository.existsByCpfHashIn(cpfHashService.hashesPossiveis(cpf))) {
            throw new BusinessException("CPF_ALREADY_REGISTERED",
                    "Este CPF já está vinculado a outra conta. Se você já tem cadastro, entre nele; "
                            + "se acha que é engano, fale com o suporte.");
        }
        user.vincularCpf(cpfHashService.hash(cpf), cpfHashService.versaoAtual());
        return true;
    }

    @Transactional
    public AuthResponse registerClient(RegisterClientRequest req, String ipAddress) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("EMAIL_IN_USE", "E-mail já cadastrado.");
        }
        User user = User.builder()
                .nome(req.nome())
                .email(req.email())
                .senhaHash(passwordEncoder.encode(req.senha()))
                .role(UserRole.ROLE_CLIENT)
                .build();
        userRepository.save(user);
        // Prova de consentimento informado (docs/PENDENCIAS_JURIDICAS.md item 3) — validação
        // @AssertTrue no request já barra cadastro sem aceite; isto é o registro da prova.
        termsAcceptanceRepository.save(
                new TermsAcceptance(user.getId(), TermsAcceptance.CURRENT_DOC_VERSION, ipAddress));
        return emitirSessao(user, UserRole.ROLE_CLIENT);
    }

    /**
     * Limite de tentativas de senha (US37): a confirmação da senha e o contador ficam em
     * {@link PasswordAuthenticator}, numa transação própria que sempre commita — achado da revisão cruzada,
     * 2026-10-05: {@code ACCOUNT_SUSPENDED} (abaixo) não entra em {@code noRollbackFor} de propósito (a recusa
     * precisa desfazer tudo o mais), mas antes disso também desfazia o ACERTO da senha que tinha acabado de
     * zerar o contador. Separado, o acerto persiste mesmo quando a conta é recusada por outro motivo depois.
     *
     * <p><b>Sem {@code @Transactional} de propósito.</b> Este método não faz nenhuma leitura/escrita própria —
     * tudo passa por {@code autenticarPorEmail} (sua própria transação) e por {@code buildAuthResponse}
     * ({@code refreshTokenRepository.save}, já transacional por padrão no Spring Data). Tentei deixar
     * {@code @Transactional} aqui "só por garantia" e quebrei o teste de 30 logins simultâneos (passo 37): o
     * Spring abre a conexão da transação ENVOLVENTE assim que o método é chamado, mesmo sem nenhum acesso ao
     * banco nela — com 30 chamadas ao mesmo tempo, cada uma segurando essa conexão ociosa enquanto espera a
     * transação própria (de dentro) terminar, o pool esgotava e sobravam respostas que não eram nem 422 nem 429.
     */
    public AuthResponse login(LoginRequest req) {
        User user = passwordAuthenticator.autenticarPorEmail(req.email(), req.senha());
        // US26: suspender bloqueia o acesso. Só DEPOIS de conferir a senha — quem não sabe as
        // credenciais não descobre que a conta está suspensa.
        if (!user.isAtivo()) {
            throw new BusinessException("ACCOUNT_SUSPENDED",
                    "Conta suspensa. Fale com o suporte em suporte@onda.app.");
        }
        return emitirSessao(user, user.getRole());   // o login abre no papel principal; /auth/switch-role troca
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String hash = sha256(req.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Token inválido ou expirado."));
        if (!stored.isValid() || !stored.getUser().isAtivo()) {
            // conta suspensa (US26) não renova a sessão — mesma resposta de token inválido
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Token inválido ou expirado.");
        }
        stored.revoke();
        refreshTokenRepository.save(stored);
        User user = stored.getUser();
        // renovar mantém o contexto da sessão (quem trocou para prestador continua prestador); se a conta perdeu o papel
        // desde então, volta ao principal
        UserRole contexto = stored.getPapel() != null && user.temPapel(stored.getPapel())
                ? stored.getPapel() : user.getRole();
        return emitirSessao(user, contexto);
    }

    /**
     * Troca o papel em uso na sessão (a conta é uma só e pode ter mais de um): emite um token no novo contexto e revoga a
     * sessão anterior, se informada. Só vale para papel que a conta TEM — cadastrar-se como prestador é
     * {@code POST /auth/become-provider}.
     */
    @Transactional
    public AuthResponse switchRole(UUID userId, SwitchRoleRequest req) {
        UserRole papel;
        try {
            papel = UserRole.valueOf(req.papel());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ROLE", "Papel inválido.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));
        if (papel == UserRole.ROLE_ADMIN || !user.temPapel(papel)) {
            throw new BusinessException("ROLE_NOT_AVAILABLE", papel == UserRole.ROLE_PROVIDER
                    ? "Esta conta ainda não é de prestador. Cadastre-se como prestador no Perfil."
                    : "Esta conta não tem esse papel.");
        }
        if (req.refreshToken() != null && !req.refreshToken().isBlank()) {
            refreshTokenRepository.findByTokenHash(sha256(req.refreshToken()))
                    .filter(anterior -> anterior.getUser().getId().equals(userId))
                    .ifPresent(anterior -> {
                        anterior.revoke();
                        refreshTokenRepository.save(anterior);
                    });
        }
        return emitirSessao(user, papel);
    }

    /**
     * Ponto único que emite a sessão: o token carrega o papel EM USO ({@code contexto}), o refresh token o guarda para a
     * renovação não trocá-lo, e a resposta lista os papéis que a conta tem (o app oferece "alternar" a partir daí).
     */
    @Transactional
    public AuthResponse emitirSessao(User user, UserRole contexto) {
        String accessToken  = jwtService.generateAccessToken(user, contexto);
        String rawRefresh   = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken(
                user,
                sha256(rawRefresh),
                Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS),
                contexto
        );
        refreshTokenRepository.save(rt);
        return new AuthResponse(accessToken, rawRefresh, contexto.name(),
                user.getId(), user.getNome(), user.getEmail(),
                user.getPapeis().stream().sorted().map(UserRole::name).toList());
    }

    private static void exigirCpfValido(String cpf) {
        if (!Cpf.valido(cpf)) {
            throw new BusinessException("INVALID_CPF", "CPF inválido. Confira os números.");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
