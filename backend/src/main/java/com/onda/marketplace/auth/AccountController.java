package com.onda.marketplace.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Autoatendimento do usuário sobre a própria conta (Cliente ou Prestador). */
@RestController
@RequestMapping("/api/v1/users/me")
public class AccountController {

    private final AccountDeletionService deletionService;

    public AccountController(AccountDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    /**
     * US36 — exclui a conta de quem chama, confirmando com a senha. É um POST de ação (como
     * {@code /cancel} e {@code /dispute}) e não um DELETE: o DELETE com corpo não tem semântica
     * definida e alguns proxies o descartam — a senha não pode se perder pelo caminho.
     */
    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@Valid @RequestBody DeleteAccountRequest req, Authentication auth) {
        // O id é o do token (subject do JWT), nunca o do corpo — mesmo padrão dos demais controllers.
        deletionService.excluir(UUID.fromString(auth.getName()), req.senha());
    }
}
