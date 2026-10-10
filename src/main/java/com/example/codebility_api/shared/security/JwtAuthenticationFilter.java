package com.example.codebility_api.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final AuthenticatedAccountResolver accountResolver;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AuthenticatedAccountResolver accountResolver,
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.accountResolver = accountResolver;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String accountId;
        try {
            Claims claims = jwtService.parse(authorization.substring(7));
            accountId = claims.getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Rejecting {} {}: invalid or expired access token ({}: {})",
                    request.getMethod(), request.getRequestURI(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            reject(request, response, "Invalid or expired access token");
            return;
        }

        if (accountId == null || accountId.isBlank()) {
            log.warn("Rejecting {} {}: access token has no subject",
                    request.getMethod(), request.getRequestURI());
            reject(request, response, "Invalid access token");
            return;
        }

        Optional<AuthenticatedAccount> account = accountResolver.resolve(accountId);
        if (account.isEmpty()) {
            log.warn("Rejecting {} {}: account {} from access token no longer exists",
                    request.getMethod(), request.getRequestURI(), accountId);
            reject(request, response, "Invalid access token");
            return;
        }

        String role = account.get().role();
        if (!AuthenticatedAccount.ROLE_ADMIN.equals(role)
                && !AuthenticatedAccount.ROLE_USER.equals(role)) {
            log.warn("Rejecting {} {}: account {} has missing or invalid role '{}'",
                    request.getMethod(), request.getRequestURI(), accountId, role);
            reject(request, response, "Invalid access token");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                account.get(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            String message) throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request, response, new BadCredentialsException(message));
    }
}
