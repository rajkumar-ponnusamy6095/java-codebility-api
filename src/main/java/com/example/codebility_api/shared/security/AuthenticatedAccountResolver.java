package com.example.codebility_api.shared.security;

import java.util.Optional;

/**
 * Loads the current state of the account referenced by an access token so
 * role changes and deletions take effect without waiting for token expiry.
 */
public interface AuthenticatedAccountResolver {

    Optional<AuthenticatedAccount> resolve(String accountId);
}
