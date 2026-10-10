package com.example.codebility_api.modules.account.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * HttpOnly refresh-token cookie scoped to the accounts routes, including the
 * context and servlet path (e.g. /api/v1/accounts) so browsers send it back.
 */
@Component
public class RefreshTokenCookies {

    static final String NAME = "refreshToken";

    private final boolean secure;
    private final String sameSite;
    private final Duration maxAge;

    public RefreshTokenCookies(
            @Value("${app.cookie.secure:false}") boolean secure,
            @Value("${app.cookie.same-site:Lax}") String sameSite,
            @Value("${app.refresh-token.expiration-ms}") long expirationMs) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.maxAge = Duration.ofMillis(expirationMs);
    }

    public String issue(HttpServletRequest request, String token) {
        return build(request, token, maxAge);
    }

    public String clear(HttpServletRequest request) {
        return build(request, "", Duration.ZERO);
    }

    private String build(HttpServletRequest request, String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(request.getContextPath() + request.getServletPath() + "/accounts")
                .maxAge(age)
                .build()
                .toString();
    }
}
