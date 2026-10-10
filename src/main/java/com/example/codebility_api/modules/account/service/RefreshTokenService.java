package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.RefreshToken;
import com.example.codebility_api.modules.account.repository.RefreshTokenRepository;
import com.example.codebility_api.shared.security.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MongoTemplate mongoTemplate;
    private final long expirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            MongoTemplate mongoTemplate,
            @Value("${app.refresh-token.expiration-ms}") long expirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.mongoTemplate = mongoTemplate;
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** Persists a new token and returns its raw value; only the hash is stored. */
    public String createToken(String accountId, String ipAddress) {
        String rawToken = SecureTokens.newToken();
        Instant now = Instant.now();
        RefreshToken token = new RefreshToken();
        token.setTokenHash(SecureTokens.sha256(rawToken));
        token.setAccountId(accountId);
        token.setCreatedAt(now);
        token.setCreatedByIp(ipAddress);
        token.setExpiresAt(now.plusMillis(expirationMs));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    public Optional<RefreshToken> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(SecureTokens.sha256(rawToken));
    }

    public Optional<RefreshToken> findActive(String rawToken) {
        return findByRawToken(rawToken).filter(RefreshToken::isActive);
    }

    /** Revokes {@code current} and issues its replacement (refresh-token rotation). */
    public String rotate(RefreshToken current, String ipAddress) {
        String replacement = createToken(current.getAccountId(), ipAddress);
        current.setRevokedAt(Instant.now());
        current.setRevokedByIp(ipAddress);
        current.setReplacedByTokenHash(SecureTokens.sha256(replacement));
        refreshTokenRepository.save(current);
        return replacement;
    }

    public void revoke(RefreshToken token, String ipAddress) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            token.setRevokedByIp(ipAddress);
            refreshTokenRepository.save(token);
        }
    }

    public void revokeAllForAccount(String accountId) {
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("accountId").is(accountId).and("revokedAt").is(null)),
                Update.update("revokedAt", Instant.now()),
                RefreshToken.class);
    }

    public void deleteAllForAccount(String accountId) {
        mongoTemplate.remove(Query.query(Criteria.where("accountId").is(accountId)), RefreshToken.class);
    }
}
