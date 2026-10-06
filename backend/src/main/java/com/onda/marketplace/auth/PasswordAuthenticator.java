package com.onda.marketplace.auth;

import com.onda.marketplace.shared.exception.BusinessException;
import com.onda.marketplace.shared.exception.PasswordMismatchException;
import com.onda.marketplace.shared.exception.TooManyAttemptsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Confere a senha e grava o contador de tentativas (US37) numa transação PRÓPRIA — {@code REQUIRES_NEW} —, que
 * sempre commita (acerto ou erro) mesmo que o método chamador decida, logo depois, desfazer tudo o que vem a seguir.
 *
 * <p>Achado da revisão cruzada (2026-10-05): {@code AuthService.login} e {@code AccountDeletionService.excluir}
 * registravam o ACERTO e DEPOIS podiam lançar outra exceção de negócio — conta suspensa (US26), pedido em curso
 * (US36) — que não está (nem deveria estar) no {@code noRollbackFor}, porque essas recusas são pra desfazer tudo.
 * Só que, na MESMA transação, isso também desfazia o acerto da senha: quem informou a senha certa, mas foi barrado
 * por outro motivo logo depois, perdia o zeramento do contador — o próximo erro de senha voltava a contar de um
 * número errado. Separar o acerto/erro numa transação própria resolve os dois casos de uma vez: a contagem persiste
 * sempre que a senha foi de fato conferida, e a decisão de negócio que vem depois continua livre pra desfazer o
 * resto.
 *
 * <p>Precisa ser um bean PRÓPRIO: {@code @Transactional(REQUIRES_NEW)} chamado de dentro da MESMA classe (self-
 * invocation) não passa pelo proxy do Spring e não abriria transação nenhuma. E quem chama não pode já estar
 * segurando a trava da linha — os dois acima não seguram: o fetch com trava é feito aqui dentro.
 */
@Service
@SuppressWarnings("null")
public class PasswordAuthenticator {

    private final UserRepository   userRepository;
    private final PasswordEncoder  passwordEncoder;
    private final PasswordAttempts passwordAttempts;

    public PasswordAuthenticator(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 PasswordAttempts passwordAttempts) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordAttempts = passwordAttempts;
    }

    /**
     * Login (US12): por e-mail, código {@code INVALID_CREDENTIALS}. Devolve o usuário (a transação já
     * commitou e fechou ao retornar — fica DETACHED; quem chama só lê os campos simples, nunca escreve nele).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {PasswordMismatchException.class, TooManyAttemptsException.class})
    public User autenticarPorEmail(String email, String senha) {
        User user = userRepository.findByEmailComTrava(email)
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Credenciais inválidas."));
        passwordAttempts.exigirLiberada(user);
        if (!passwordEncoder.matches(senha, user.getSenhaHash())) {
            passwordAttempts.registrarErro(user);
            throw new PasswordMismatchException("INVALID_CREDENTIALS", "Credenciais inválidas.");
        }
        passwordAttempts.registrarAcerto(user);
        return user;
    }

    /**
     * Exclusão de conta (US36): por id, código {@code INVALID_PASSWORD}. O {@code ADMIN_CANNOT_DELETE} mora aqui
     * (não depois): não faz sentido gastar uma tentativa de senha de um fluxo que o admin nunca completaria. Conta
     * já excluída: idempotência de verdade — nem confere senha (toque duplo não pode exigir a senha de novo da
     * "perdedora" da corrida) nem conta como tentativa.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {PasswordMismatchException.class, TooManyAttemptsException.class})
    public void autenticarPorId(UUID userId, String senha) {
        User user = userRepository.findByIdComTrava(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Usuário não encontrado."));
        if (user.isExcluido()) {
            return;
        }
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            throw new BusinessException("ADMIN_CANNOT_DELETE",
                    "Administradores não excluem a conta por aqui.");
        }
        passwordAttempts.exigirLiberada(user);
        if (!passwordEncoder.matches(senha, user.getSenhaHash())) {
            passwordAttempts.registrarErro(user);
            throw new PasswordMismatchException("INVALID_PASSWORD", "Senha incorreta.");
        }
        passwordAttempts.registrarAcerto(user);
    }
}
