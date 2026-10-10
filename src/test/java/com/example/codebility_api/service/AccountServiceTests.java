package com.example.codebility_api.service;

import com.example.codebility_api.dto.AccountListResponse;
import com.example.codebility_api.dto.CreateAccountRequest;
import com.example.codebility_api.dto.LoginRequest;
import com.example.codebility_api.dto.LoginResponse;
import com.example.codebility_api.dto.UpdateAccountRequest;
import com.example.codebility_api.entity.Account;
import com.example.codebility_api.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTests {

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);

        accountService = new AccountService(
                accountRepository,
                passwordEncoder,
                mock(EmailService.class),
                jwtService,
                mock(MongoTemplate.class),
                refreshTokenService
        );

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Account account(String id, String gender) {
        Account account = new Account();
        account.setId(id);
        account.setEmail(id + "@example.com");
        account.setFirstName("First");
        account.setLastName("Last");
        account.setGender(gender);
        account.setRole("User");
        account.setStatus("active");
        account.setPasswordHash("hash");
        account.setVerified(Instant.now());
        return account;
    }

    @Test
    void getAccountByIdIncludesGender() {
        when(accountRepository.findById("a1"))
                .thenReturn(Optional.of(account("a1", "Female")));

        assertThat(accountService.getAccountById("a1").gender())
                .isEqualTo("Female");
    }

    @Test
    void getAllAccountsIncludesGender() {
        when(accountRepository.findAll()).thenReturn(List.of(
                account("a1", "Female"),
                account("a2", "Male")
        ));

        AccountListResponse response = accountService.getAllAccounts();

        assertThat(response.accounts())
                .extracting(LoginResponse.AccountDetails::gender)
                .containsExactly("Female", "Male");
    }

    @Test
    void authenticateIncludesGender() {
        Account account = account("a1", "Female");
        when(accountRepository.findByEmail("a1@example.com"))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret-password", "hash")).thenReturn(true);
        when(jwtService.generateToken(anyString(), anyString())).thenReturn("jwt");
        when(refreshTokenService.createToken("a1")).thenReturn("refresh");

        LoginResponse response = accountService
                .authenticate(new LoginRequest("a1@example.com", "secret-password"))
                .response();

        assertThat(response.account().gender()).isEqualTo("Female");
    }

    @Test
    void createAccountTrimsGenderAndReturnsIt() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");

        LoginResponse.AccountDetails created = accountService.createAccount(
                new CreateAccountRequest(
                        "new@example.com", "secret-password", "New", "User",
                        "  Female  ", null, null, "User", null
                ));

        assertThat(created.gender()).isEqualTo("Female");
    }

    @Test
    void updateAccountTrimsGenderAndReturnsIt() {
        when(accountRepository.findById("a1"))
                .thenReturn(Optional.of(account("a1", "Male")));
        when(accountRepository.findByEmail("a1@example.com"))
                .thenReturn(Optional.empty());

        LoginResponse.AccountDetails updated = accountService.updateAccount(
                "a1",
                new UpdateAccountRequest(
                        "a1@example.com", "First", "Last", "  Female ",
                        null, null, "User", "active"
                ));

        assertThat(updated.gender()).isEqualTo("Female");
    }

    @Test
    void validateResetTokenUsesRepositoryLookup() {
        Account account = account("a1", "Female");
        account.setResetToken(new Account.ResetToken(
                "ignored", Instant.now().plusSeconds(600)
        ));
        when(accountRepository.findByResetTokenToken(anyString()))
                .thenReturn(Optional.of(account));

        assertThat(accountService.validateResetToken("raw-token")).isTrue();
        verify(accountRepository, never()).findAll();
    }

    @Test
    void validateResetTokenRejectsExpiredToken() {
        Account account = account("a1", "Female");
        account.setResetToken(new Account.ResetToken(
                "ignored", Instant.now().minusSeconds(1)
        ));
        when(accountRepository.findByResetTokenToken(anyString()))
                .thenReturn(Optional.of(account));

        assertThat(accountService.validateResetToken("raw-token")).isFalse();
    }
}
