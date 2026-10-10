package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.domain.RefreshToken;
import com.example.codebility_api.modules.account.dto.AuthenticationResult;
import com.example.codebility_api.modules.account.dto.LoginRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import com.example.codebility_api.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTests {

    private static final String ACCOUNT_ID = "64b000000000000000000001";

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private AuthService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        service = new AuthService(accountRepository, passwordEncoder, jwtService, refreshTokenService);
        when(jwtService.generateToken(ACCOUNT_ID, "User")).thenReturn("jwt");
    }

    private static Account account(boolean verified, String status) {
        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setEmail("jane@example.com");
        account.setPasswordHash("hash");
        account.setRole("User");
        account.setStatus(status);
        account.setVerified(verified ? Instant.now() : null);
        return account;
    }

    @Test
    void authenticateReturnsEmailJwtAndRefreshToken() {
        when(accountRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(account(true, "active")));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(refreshTokenService.createToken(ACCOUNT_ID, "1.2.3.4")).thenReturn("refresh");

        AuthenticationResult result = service.authenticate(new LoginRequest(" Jane@Example.com ", "secret"), "1.2.3.4");

        assertThat(result).isEqualTo(new AuthenticationResult("jane@example.com", "jwt", "refresh"));
    }

    @Test
    void unverifiedAccountGetsGenericCredentialError() {
        when(accountRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(account(false, "active")));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticate(new LoginRequest("jane@example.com", "secret"), "ip"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getReason()).isEqualTo("Email or password is incorrect");
                });
    }

    @Test
    void refreshRotatesToken() {
        RefreshToken current = new RefreshToken();
        current.setAccountId(ACCOUNT_ID);
        when(refreshTokenService.findActive("old")).thenReturn(Optional.of(current));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(true, "active")));
        when(refreshTokenService.rotate(current, "ip")).thenReturn("new");

        AuthenticationResult result = service.refresh("old", "ip");

        assertThat(result.refreshToken()).isEqualTo("new");
        assertThat(result.jwtToken()).isEqualTo("jwt");
    }

    @Test
    void refreshWithUnknownTokenReturns401() {
        when(refreshTokenService.findActive("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("bad", "ip"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void userCannotRevokeAnotherUsersToken() {
        RefreshToken token = new RefreshToken();
        token.setAccountId("64b000000000000000000009");
        when(refreshTokenService.findActive("t")).thenReturn(Optional.of(token));
        AuthenticatedAccount caller = new AuthenticatedAccount(ACCOUNT_ID, "jane@example.com", "User");

        assertThatThrownBy(() -> service.revoke("t", "ip", caller))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(refreshTokenService, never()).revoke(any(), any());
    }

    @Test
    void adminCanRevokeAnyToken() {
        RefreshToken token = new RefreshToken();
        token.setAccountId("64b000000000000000000009");
        when(refreshTokenService.findActive("t")).thenReturn(Optional.of(token));

        service.revoke("t", "ip", new AuthenticatedAccount(ACCOUNT_ID, "a@example.com", "Admin"));

        verify(refreshTokenService).revoke(token, "ip");
    }
}
