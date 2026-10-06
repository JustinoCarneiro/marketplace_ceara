package com.onda.marketplace.provider;

import com.onda.marketplace.auth.AuthResponse;
import com.onda.marketplace.auth.AuthService;
import com.onda.marketplace.auth.TermsAcceptance;
import com.onda.marketplace.auth.TermsAcceptanceRepository;
import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import com.onda.marketplace.shared.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@SuppressWarnings("null")
public class ProviderService {

    private final UserRepository            userRepository;
    private final ProviderProfileRepository profileRepository;
    private final AuthService               authService;
    private final PasswordEncoder           passwordEncoder;
    private final CpfEncryptor              cpfEncryptor;
    private final BackgroundCheckService    backgroundCheckService;
    private final TermsAcceptanceRepository termsAcceptanceRepository;

    public ProviderService(
            UserRepository userRepository,
            ProviderProfileRepository profileRepository,
            AuthService authService,
            PasswordEncoder passwordEncoder,
            CpfEncryptor cpfEncryptor,
            BackgroundCheckService backgroundCheckService,
            TermsAcceptanceRepository termsAcceptanceRepository) {
        this.userRepository            = userRepository;
        this.profileRepository         = profileRepository;
        this.authService               = authService;
        this.passwordEncoder           = passwordEncoder;
        this.cpfEncryptor              = cpfEncryptor;
        this.backgroundCheckService    = backgroundCheckService;
        this.termsAcceptanceRepository = termsAcceptanceRepository;
    }

    @Transactional
    public AuthResponse register(RegisterProviderRequest req, String ipAddress) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("EMAIL_IN_USE",
                    "E-mail já cadastrado. Se você já tem conta, entre nela e toque em \"Quero ser prestador\" no Perfil.");
        }
        // PROVIDER principal; a conta também tem o papel de cliente (todo prestador contrata) — ver User.
        User user = User.builder()
                .nome(req.nome())
                .email(req.email())
                .senhaHash(passwordEncoder.encode(req.senha()))
                .role(UserRole.ROLE_PROVIDER)
                .build();
        // valida os dígitos, recusa CPF de outra conta e grava só o hash (uma pessoa = um CPF)
        authService.vincularCpf(user, req.cpf());
        userRepository.save(user);
        termsAcceptanceRepository.save(
                new TermsAcceptance(user.getId(), TermsAcceptance.CURRENT_DOC_VERSION, ipAddress));
        criarPerfil(user, req.categoria(), req.cpf(), req.bio());
        return authService.emitirSessao(user, UserRole.ROLE_PROVIDER);
    }

    /**
     * Quem já tem conta (cliente) passa a prestar serviço NA MESMA conta — conta única com papéis. Exige o CPF: se a conta já
     * o confirmou (1º pagamento), tem de ser o mesmo. A conta fica {@code EM_VERIFICACAO} como qualquer prestador novo e a
     * sessão volta no contexto de prestador. Lê a conta com trava de linha: o toque duplo não cria dois perfis.
     */
    @Transactional
    public AuthResponse tornarPrestador(UUID userId, BecomeProviderRequest req, String ipAddress) {
        User user = userRepository.findByIdComTrava(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));
        if (user.temPapel(UserRole.ROLE_PROVIDER)) {
            throw new BusinessException("ALREADY_PROVIDER", "Esta conta já é de prestador.");
        }
        if (!user.temPapel(UserRole.ROLE_CLIENT)) {
            throw new BusinessException("ROLE_NOT_AVAILABLE", "Esta conta não pode virar prestador.");
        }
        authService.consumirRefreshDaConta(req.refreshToken(), userId);   // achado da revisão cruzada, 2026-10-05
        authService.vincularCpf(user, req.cpf());   // INVALID_CPF / CPF_MISMATCH / CPF_ALREADY_REGISTERED
        user.concederPapel(UserRole.ROLE_PROVIDER);
        userRepository.save(user);
        termsAcceptanceRepository.save(
                new TermsAcceptance(user.getId(), TermsAcceptance.CURRENT_DOC_VERSION, ipAddress));
        criarPerfil(user, req.categoria(), req.cpf(), req.bio());
        return authService.emitirSessao(user, UserRole.ROLE_PROVIDER);
    }

    private void criarPerfil(User user, String categoria, String cpf, String bio) {
        ProviderProfile profile = new ProviderProfile(user, categoria, cpfEncryptor.encrypt(cpf));
        if (bio != null && !bio.isBlank()) {
            profile.setBio(bio);
        }
        profileRepository.save(profile);
        backgroundCheckService.scheduleCheck(profile);
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
}
