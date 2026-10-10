
package com.example.codebility_api.service;

import com.example.codebility_api.entity.RefreshToken;
import com.example.codebility_api.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long expirationMs;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.refresh-token.expiration-ms:604800000}")
            long expirationMs) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationMs = expirationMs;
    }

    public String createToken(String accountId) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        Instant now = Instant.now();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setAccountId(accountId);
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusMillis(expirationMs));

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    public RefreshToken validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        RefreshToken token = refreshTokenRepository
                .findByTokenHash(hashToken(rawToken))
                .orElseThrow(this::invalidToken);

        if (token.getRevokedAt() != null
                || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired refresh token"
            );
        }

        return token;
    }

    public void revokeToken(RefreshToken token) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 is not available", ex);
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired refresh token"
        );
    }


    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(rawToken))
                .ifPresent(this::revokeToken);
    }

    public void revokeAllForAccount(String accountId) {
        refreshTokenRepository.findAllByAccountId(accountId)
                .stream()
                .filter(token -> token.getRevokedAt() == null)
                .forEach(this::revokeToken);
    }

}
