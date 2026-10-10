package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import com.example.codebility_api.shared.security.AuthenticatedAccountResolver;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AccountPrincipalResolver implements AuthenticatedAccountResolver {

    private final MongoTemplate mongoTemplate;

    public AccountPrincipalResolver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<AuthenticatedAccount> resolve(String accountId) {
        if (!ObjectId.isValid(accountId)) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("id").is(accountId));
        query.fields().include("email", "role");
        return Optional.ofNullable(mongoTemplate.findOne(query, Account.class))
                .map(account -> new AuthenticatedAccount(account.getId(), account.getEmail(), account.getRole()));
    }
}
