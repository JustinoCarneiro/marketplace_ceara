package com.onda.marketplace.auth;

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
    private final long                      refreshTokenDays;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            CpfHashService cpfHashService,
            TermsAcceptanceRepository termsAcceptanceRepository,
            @Value("${jwt.refresh-token-days:30}") long refreshTokenDays) {
        this.userRepository            = userRepository;
        this.refreshTokenRepository    = refreshTokenRepository;
        this.jwtService                = jwtService;
        this.passwordEncoder           = passwordEncoder;
        this.cpfHashService            = cpfHashService;
        this.termsAcceptanceRepository = termsAcceptanceRepository;
        this.refreshTokenDays          = refreshTokenDays;
    }

    /**
     * Registra o CPF do cliente como hash determinístico (HMAC-SHA256) no primeiro pagamento.
     * Garante unicidade de pessoa na plataforma sem armazenar o CPF em claro (LGPD).
     */
    @Transactional
    public void verifyIdentity(String cpf, UUID userId) {
        String hash = cpfHashService.hash(cpf);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));

        // Idempotente de verdade: precisa checar o hash ANTES do existsByCpfHash global, senão
        // um retry com o mesmo CPF já verificado colide com o próprio registro do usuário e
        // vira CPF_ALREADY_REGISTERED — mensagem errada ("outra conta" sendo a conta dele mesmo).
        if (hash.equals(user.getCpfHash())) {
            return;
        }
        if (userRepository.existsByCpfHash(hash)) {
            throw new BusinessException("CPF_ALREADY_REGISTERED",
                    "Este CPF já está vinculado a outra conta.");
        }
        user.setCpfHash(hash);
        userRepository.save(user);
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
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Credenciais inválidas."));
        if (!passwordEncoder.matches(req.senha(), user.getSenhaHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Credenciais inválidas.");
        }
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String hash = sha256(req.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Token inválido ou expirado."));
        if (!stored.isValid()) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Token inválido ou expirado.");
        }
        stored.revoke();
        refreshTokenRepository.save(stored);
        return buildAuthResponse(stored.getUser());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String rawRefresh   = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken(
                user,
                sha256(rawRefresh),
                Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS)
        );
        refreshTokenRepository.save(rt);
        return new AuthResponse(accessToken, rawRefresh, user.getRole().name(),
                user.getId(), user.getNome(), user.getEmail());
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
