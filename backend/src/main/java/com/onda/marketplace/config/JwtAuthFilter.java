package com.onda.marketplace.config;

import com.onda.marketplace.auth.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Autentica pela assinatura do JWT <b>e</b> confere, a cada requisição, se a conta continua ativa.
 *
 * <p>Sem a segunda checagem um access token já emitido valia até expirar (15 min) mesmo depois de
 * a conta ser suspensa ou excluída: o `ativo` só era conferido no login e no refresh. O custo é uma
 * consulta por chave primária por requisição autenticada.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final Predicate<UUID> contaAtiva;

    public JwtAuthFilter(JwtService jwtService, Predicate<UUID> contaAtiva) {
        this.jwtService = jwtService;
        this.contaAtiva = contaAtiva;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validateAndExtract(token);
                UUID userId   = UUID.fromString(claims.getSubject());
                // conta suspensa, excluída ou inexistente: segue sem autenticação (o endpoint protegido dá 401)
                if (contaAtiva.test(userId)) {
                    String role = claims.get("role", String.class);
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            List.of(new SimpleGrantedAuthority(role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // token inválido — segue sem autenticação; endpoint protegido retornará 401
            }
        }
        chain.doFilter(req, res);
    }
}
