package com.onda.marketplace.config;

import com.onda.marketplace.auth.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Antes o filtro só validava o token: o `ativo` da conta era conferido no login e no refresh, mas
 * um access token já emitido seguia valendo até expirar (15 min) — conta suspensa ou excluída
 * continuava usando o app. Agora cada requisição confere se a conta está ativa.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock JwtService jwtService;
    @Mock Claims claims;

    final Set<UUID> contasAtivas = new HashSet<>();
    final FilterChain chain = mock(FilterChain.class);
    JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthFilter(jwtService, contasAtivas::contains);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void executa(String authorization) throws Exception {
        var req = new MockHttpServletRequest();
        if (authorization != null) req.addHeader("Authorization", authorization);
        filter.doFilter(req, new MockHttpServletResponse(), chain);
    }

    private void tokenValidoDe(String subject) {
        when(jwtService.validateAndExtract("bom")).thenReturn(claims);
        when(claims.getSubject()).thenReturn(subject);
    }

    @Test
    void tokenValido_deContaAtiva_autentica() throws Exception {
        contasAtivas.add(USER_ID);
        tokenValidoDe(USER_ID.toString());
        when(claims.get("role", String.class)).thenReturn("ROLE_CLIENT");

        executa("Bearer bom");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(USER_ID.toString());
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_CLIENT");
        verify(chain).doFilter(any(), any());
    }

    @Test
    void tokenValido_deContaInativa_naoAutentica_mesmoComATokenAindaDentroDaValidade() throws Exception {
        // suspensa, excluída ou inexistente: para o filtro é tudo "conta que não está ativa"
        tokenValidoDe(USER_ID.toString());
        // Com o role VÁLIDO, a única razão de não autenticar é a conta inativa. Sem este stub, o role nulo faria
        // SimpleGrantedAuthority lançar, o catch engoliria, e o teste passaria MESMO se o filtro ignorasse a
        // conta (a mutação `if (true)` sobreviveu assim). lenient: no código certo ele nem chega a ser lido.
        lenient().when(claims.get("role", String.class)).thenReturn("ROLE_CLIENT");

        executa("Bearer bom");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // a requisição segue: é o Spring Security que devolve 401 no endpoint protegido
        verify(chain).doFilter(any(), any());
    }

    @Test
    void tokenInvalido_naoAutentica() throws Exception {
        when(jwtService.validateAndExtract("ruim")).thenThrow(new JwtException("assinatura inválida"));

        executa("Bearer ruim");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void subjectQueNaoEhUuid_naoAutentica_eNaoLanca() throws Exception {
        tokenValidoDe("nao-e-um-uuid");

        executa("Bearer bom");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void semCabecalho_naoAutentica_eSeguePelaCadeia() throws Exception {
        executa(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }
}
