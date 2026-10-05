package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.auth.CpfHashService;
import com.onda.marketplace.auth.JwtService;
import com.onda.marketplace.auth.RefreshToken;
import com.onda.marketplace.auth.RefreshTokenRepository;
import com.onda.marketplace.auth.TermsAcceptance;
import com.onda.marketplace.auth.TermsAcceptanceRepository;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
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
public class ProviderService {

    private final UserRepository            userRepository;
    private final ProviderProfileRepository profileRepository;
    private final RefreshTokenRepository    refreshTokenRepository;
    private final JwtService                jwtService;
    private final PasswordEncoder           passwordEncoder;
    private final CpfEncryptor              cpfEncryptor;
    private final CpfHashService            cpfHashService;
    private final BackgroundCheckService    backgroundCheckService;
    private final TermsAcceptanceRepository termsAcceptanceRepository;
    private final long                      refreshTokenDays;

    public ProviderService(
            UserRepository userRepository,
            ProviderProfileRepository profileRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            CpfEncryptor cpfEncryptor,
            CpfHashService cpfHashService,
            BackgroundCheckService backgroundCheckService,
            TermsAcceptanceRepository termsAcceptanceRepository,
            @Value("${jwt.refresh-token-days:30}") long refreshTokenDays) {
        this.userRepository            = userRepository;
        this.profileRepository         = profileRepository;
        this.refreshTokenRepository    = refreshTokenRepository;
        this.jwtService                = jwtService;
        this.passwordEncoder           = passwordEncoder;
        this.cpfEncryptor              = cpfEncryptor;
        this.cpfHashService            = cpfHashService;
        this.backgroundCheckService    = backgroundCheckService;
        this.termsAcceptanceRepository = termsAcceptanceRepository;
        this.refreshTokenDays          = refreshTokenDays;
    }

    @Transactional
    public AuthResponse register(RegisterProviderRequest req, String ipAddress) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("EMAIL_IN_USE", "E-mail já cadastrado.");
        }
        // Uma pessoa = um CPF (antifraude, Camada 2): sem validar os dígitos um número inventado burlaria a unicidade; o
        // hash é de só os dígitos, então a mesma pessoa tem uma identidade só, com ou sem máscara.
        if (!Cpf.valido(req.cpf())) {
            throw new BusinessException("INVALID_CPF", "CPF inválido. Confira os números.");
        }
        String cpfHash = cpfHashService.hash(req.cpf());
        if (userRepository.existsByCpfHash(cpfHash)) {
            throw new BusinessException("CPF_ALREADY_REGISTERED", "Este CPF já está vinculado a outra conta.");
        }
        User user = User.builder()
                .nome(req.nome())
                .email(req.email())
                .senhaHash(passwordEncoder.encode(req.senha()))
                .role(UserRole.ROLE_PROVIDER)
                .build();
        user.setCpfHash(cpfHash);
        userRepository.save(user);
        termsAcceptanceRepository.save(
                new TermsAcceptance(user.getId(), TermsAcceptance.CURRENT_DOC_VERSION, ipAddress));

        String cpfCifrado = cpfEncryptor.encrypt(req.cpf());
        ProviderProfile profile = new ProviderProfile(user, req.categoria(), cpfCifrado);
        if (req.bio() != null && !req.bio().isBlank()) {
            profile.setBio(req.bio());
        }
        profileRepository.save(profile);

        backgroundCheckService.scheduleCheck(profile);

        return buildAuthResponse(user);
    }

    /**
     * Cadastra/atualiza a chave Pix do prestador — destino do repasse na conclusão
     * (Modelo A, MKT-49). Cifrada em repouso (mesmo esquema do CPF); nunca volta em
     * claro num DTO.
     */
    @Transactional
    public void atualizarChavePix(UUID userId, String chavePixClaro) {
        if (chavePixClaro == null || chavePixClaro.isBlank()) {
            throw new BusinessException("PIX_KEY_REQUIRED", "Informe a chave Pix.");
        }
        ProviderProfile perfil = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(
                        "PROVIDER_NOT_FOUND", "Perfil de prestador não encontrado."));
        // Validada e guardada na forma canônica: é o destino de dinheiro real do repasse.
        PixKey chave = PixKey.parse(chavePixClaro);
        perfil.setChavePixCifrada(cpfEncryptor.encrypt(chave.valor()));
        profileRepository.save(perfil);
    }

    @Transactional(readOnly = true)
    public boolean chavePixCadastrada(UUID userId) {
        return profileRepository.findByUserId(userId)
                .map(p -> p.getChavePixCifrada() != null && !p.getChavePixCifrada().isBlank())
                .orElse(false);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh  = UUID.randomUUID().toString();
        RefreshToken rt = new RefreshToken(
                user, sha256(rawRefresh),
                Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS));
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
