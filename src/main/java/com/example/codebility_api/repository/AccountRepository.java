
package com.example.codebility_api.repository;

import com.example.codebility_api.entity.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface AccountRepository extends MongoRepository<Account, String> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Account> findByVerificationToken(String verificationToken);

    @Query("{}")
    List<Account> findAllAccounts(Pageable pageable);

    @Query("{ '_id': { '$gt': ?0 } }")
    List<Account> findAccountsAfterId(String afterId, Pageable pageable);

    @Query(value = "{}", count = true)
    long countAllAccounts();

    @Query(value = "{ '_id': { '$gt': ?0 } }", count = true)
    long countAccountsAfterId(String afterId);
}
