package com.onda.marketplace.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "teste-chave-secreta-min-32-caracteres-ok";

    private JwtService service(long accessTokenMs) {
        return new JwtService(SECRET, accessTokenMs);
    }

    private User usuario() {
        var u = User.builder().nome("Zé Elétrica").email("ze@teste.com")
                .senhaHash("$2a$hash").role(UserRole.ROLE_PROVIDER).build();
        setId(u, UUID.randomUUID());
        return u;
    }

    @Test
    void generateAccessToken_produzTokenValidoComClaimsCorretas() {
        var svc = service(900_000);
        var user = usuario();

        String token = svc.generateAccessToken(user);

        assertThat(svc.isValid(token)).isTrue();
        assertThat(svc.extractUserId(token)).isEqualTo(user.getId());
        assertThat(svc.extractRole(token)).isEqualTo("ROLE_PROVIDER");
    }

    @Test
    void validateAndExtract_devolveClaimsDeEmailERole() {
        var svc = service(900_000);
        var user = usuario();

        var claims = svc.validateAndExtract(svc.generateAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email", String.class)).isEqualTo("ze@teste.com");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_PROVIDER");
    }

    @Test
    void isValid_tokenAdulterado_retornaFalse() {
        // Duas chaves diferentes: token gerado por uma nunca valida na outra — é a garantia
        // básica de que ninguém forja token sem conhecer o segredo do servidor.
        var svcEmissor    = service(900_000);
        var svcOutraChave = new JwtService("outra-chave-secreta-completamente-diferente-ok", 900_000);

        String token = svcEmissor.generateAccessToken(usuario());

        assertThat(svcOutraChave.isValid(token)).isFalse();
    }

    @Test
    void isValid_tokenExpirado_retornaFalse() {
        // accessTokenMs negativo: expiration = now + (negativo) já nasce no passado —
        // evita precisar de sleep pra testar expiração de verdade.
        var svc = service(-10_000);

        String token = svc.generateAccessToken(usuario());

        assertThat(svc.isValid(token)).isFalse();
    }

    @Test
    void isValid_lixoNaoJwt_retornaFalseSemLancarExcecao() {
        var svc = service(900_000);

        assertThat(svc.isValid("isso.nao.eh-um-jwt")).isFalse();
        assertThat(svc.isValid("")).isFalse();
    }

    @Test
    void extractUserId_tokenExpirado_lancaJwtException() {
        var svc = service(-10_000);
        String token = svc.generateAccessToken(usuario());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> svc.extractUserId(token))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    private static void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
