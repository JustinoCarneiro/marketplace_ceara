package com.onda.marketplace.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService          authService;
    private final PasswordResetService passwordResetService;
    private final long                 pisoRespostaMs;

    public AuthController(AuthService authService,
                          PasswordResetService passwordResetService,
                          @Value("${marketplace.password-reset.resposta-minima-ms:300}") long pisoRespostaMs) {
        this.authService          = authService;
        this.passwordResetService = passwordResetService;
        this.pisoRespostaMs       = pisoRespostaMs;
    }

    /**
     * US35 — pede o código de recuperação de senha. A resposta é SEMPRE a mesma, exista o e-mail
     * ou não. O tempo também: o e-mail só é enviado depois do commit e em outra thread, e a
     * resposta espera um piso fixo (fora da transação) para a pequena diferença de trabalho entre
     * e-mail cadastrado e desconhecido não virar um detector de contas.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        long inicio = System.nanoTime();
        try {
            passwordResetService.solicitar(req.email());
        } finally {
            aguardarPiso(inicio);
        }
        return ForgotPasswordResponse.padrao();
    }

    /** US35 — troca a senha com o código recebido; encerra todas as sessões abertas. */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.redefinir(req.email(), req.codigo(), req.novaSenha());
    }

    private void aguardarPiso(long inicioNanos) {
        long faltaMs = pisoRespostaMs - (System.nanoTime() - inicioNanos) / 1_000_000;
        if (faltaMs > 0) {
            try {
                Thread.sleep(faltaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** US01 — Cadastro do Cliente */
    @PostMapping("/register/client")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerClient(@Valid @RequestBody RegisterClientRequest req, HttpServletRequest httpReq) {
        return authService.registerClient(req, httpReq.getRemoteAddr());
    }

    /** US12 — Login */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** US12 — Refresh token */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req);
    }

    /** Antifraude Camada 2 — vincula CPF ao cliente no 1º pagamento (LGPD: só o hash é guardado) */
    @PostMapping("/verify-identity")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyIdentity(@Valid @RequestBody VerifyIdentityRequest req,
                               Authentication auth) {
        // JwtAuthFilter define o principal como o id do usuário (subject do JWT),
        // não um UserDetails — mesmo padrão dos demais controllers.
        UUID userId = UUID.fromString(auth.getName());
        authService.verifyIdentity(req.cpf(), userId);
    }
}
