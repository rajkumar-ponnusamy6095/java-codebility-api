package com.example.codebility_api.modules.account.repository;

import com.example.codebility_api.modules.account.domain.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AccountRepository extends MongoRepository<Account, String> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Account> findByVerificationToken(String verificationToken);

    Optional<Account> findByResetTokenToken(String token);
}
