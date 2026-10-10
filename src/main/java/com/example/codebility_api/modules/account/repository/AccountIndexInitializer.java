package com.example.codebility_api.modules.account.repository;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.domain.RefreshToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Creates the indexes the account module's queries rely on. Index creation is
 * idempotent; set app.mongodb.ensure-indexes=false where indexes are managed
 * by migrations instead.
 */
@Component
@ConditionalOnProperty(name = "app.mongodb.ensure-indexes", havingValue = "true", matchIfMissing = true)
public class AccountIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(AccountIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public AccountIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        IndexOperations accounts = mongoTemplate.indexOps(Account.class);
        accounts.createIndex(new Index().on("email", Sort.Direction.ASC).unique());
        accounts.createIndex(new Index().on("verificationToken", Sort.Direction.ASC).sparse());
        accounts.createIndex(new Index().on("resetToken.token", Sort.Direction.ASC).sparse());
        accounts.createIndex(new Index().on("role", Sort.Direction.ASC).on("_id", Sort.Direction.ASC));
        accounts.createIndex(new Index().on("status", Sort.Direction.ASC).on("_id", Sort.Direction.ASC));
        accounts.createIndex(new Index().on("department", Sort.Direction.ASC).on("_id", Sort.Direction.ASC));
        accounts.createIndex(new Index().on("createdAt", Sort.Direction.DESC));

        IndexOperations refreshTokens = mongoTemplate.indexOps(RefreshToken.class);
        refreshTokens.createIndex(new Index().on("tokenHash", Sort.Direction.ASC).unique());
        refreshTokens.createIndex(new Index().on("accountId", Sort.Direction.ASC));
        refreshTokens.createIndex(new Index().on("expiresAt", Sort.Direction.ASC).expire(Duration.ZERO));

        log.info("Account module indexes ensured");
    }
}
