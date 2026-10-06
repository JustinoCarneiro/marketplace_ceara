package com.onda.marketplace.auth;

import com.onda.marketplace.shared.exception.TooManyAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Limite de tentativas de senha — a mesma regra para o login e para a confirmação da exclusão de conta. Depois de
 * {@code max-failures} erros seguidos a conta não aceita nova tentativa por {@code lock-seconds}; um acerto, ou a
 * redefinição da senha pelo e-mail, zera tudo. Sem isso a senha podia ser testada sem fim.
 *
 * <p>Quem usa precisa: ler a conta com trava de linha (palpites simultâneos se enfileiram em vez de lerem o mesmo
 * contador — ver {@code UserRepository.findByIdComTrava}); chamar {@link #exigirLiberada} ANTES de conferir a senha
 * (durante o bloqueio nem a senha certa entra); e declarar {@code noRollbackFor} para
 * {@code PasswordMismatchException}/{@code TooManyAttemptsException}, senão o contador volta junto com a exceção.
 *
 * <p>Trade-off assumido: como o bloqueio é por conta, quem sabe o e-mail de alguém pode bloqueá-lo por
 * {@code lock-seconds}. O bloqueio é curto, não impede a recuperação por e-mail (que o encerra) e só ataca quem já
 * seria alvo de força bruta.
 */
@Component
public class PasswordAttempts {

    private final int      limite;
    private final Duration bloqueio;

    public PasswordAttempts(@Value("${marketplace.password-attempts.max-failures:5}") int limite,
                            @Value("${marketplace.password-attempts.lock-seconds:900}") long bloqueioSegundos) {
        this.limite   = limite;
        this.bloqueio = Duration.ofSeconds(bloqueioSegundos);
    }

    /**
     * @throws TooManyAttemptsException se a conta está bloqueada para novas tentativas
     *
     * <p>O tempo que falta vem do {@code senha_bloqueada_ate} GRAVADO, nunca da configuração atual — achado da
     * revisão cruzada (2026-10-05): um {@code Math.min} com {@code lock-seconds} aqui fazia a resposta anunciar
     * um tempo mais curto do que o bloqueio realmente dura se a configuração fosse reduzida depois de o bloqueio
     * ter sido gravado (ex.: 900s → 300s com um bloqueio de 900s em andamento: a pessoa tentava de novo aos 5
     * minutos, anunciados, e encontrava a conta ainda bloqueada por mais 10).
     */
    public void exigirLiberada(User user) {
        Instant agora = Instant.now();
        if (user.senhaBloqueada(agora)) {
            long faltam = Duration.between(agora, user.getSenhaBloqueadaAte()).toSeconds() + 1;
            throw new TooManyAttemptsException(faltam);
        }
    }

    public void registrarErro(User user) {
        user.registrarSenhaErrada(Instant.now(), limite, bloqueio);
    }

    public void registrarAcerto(User user) {
        user.limparTentativasDeSenha();
    }
}
