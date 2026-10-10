package com.example.codebility_api.modules.account.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Refresh tokens are stored as SHA-256 hashes. Expired documents are removed
 * by the TTL index on {@code expiresAt} (see AccountIndexInitializer).
 */
@Document(collection = "refreshTokens")
public class RefreshToken {

    @Id
    private String id;
    private String tokenHash;
    private String accountId;
    private Instant expiresAt;
    private Instant createdAt;
    private String createdByIp;
    private Instant revokedAt;
    private String revokedByIp;
    private String replacedByTokenHash;

    public boolean isActive() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getCreatedByIp() { return createdByIp; }
    public void setCreatedByIp(String createdByIp) { this.createdByIp = createdByIp; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedByIp() { return revokedByIp; }
    public void setRevokedByIp(String revokedByIp) { this.revokedByIp = revokedByIp; }
    public String getReplacedByTokenHash() { return replacedByTokenHash; }
    public void setReplacedByTokenHash(String replacedByTokenHash) { this.replacedByTokenHash = replacedByTokenHash; }
}
