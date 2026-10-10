
package com.example.codebility_api.repository;

import com.example.codebility_api.entity.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface RefreshTokenRepository
        extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findAllByAccountId(String accountId);
}
