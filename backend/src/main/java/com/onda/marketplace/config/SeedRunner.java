package com.onda.marketplace.config;

import com.onda.marketplace.auth.User;
import com.onda.marketplace.auth.UserRepository;
import com.onda.marketplace.auth.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Semeia os usuários mínimos exigidos pelos testes E2E do painel admin (Playwright).
 * Ativo APENAS com o profile {@code seed} (usado no CI de E2E do admin) — nunca em
 * produção. Idempotente: só cria o usuário se o e-mail ainda não existir.
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("Administrador Onda", "admin@onda.com",  "admin123",  UserRole.ROLE_ADMIN);
        seed("Maria Teste",        "maria@teste.com", "Senha@123", UserRole.ROLE_CLIENT);
    }

    private void seed(String nome, String email, String senha, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            log.info("[seed] {} já existe — ignorando", email);
            return;
        }
        User user = User.builder()
                .nome(nome)
                .email(email)
                .senhaHash(passwordEncoder.encode(senha))
                .role(role)
                .build();
        userRepository.save(user);
        log.info("[seed] usuário {} ({}) criado", email, role);
    }
}
