package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.RegisterRequest;
import com.example.codebility_api.modules.account.dto.ResetPasswordRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationAndPasswordServiceTests {

    private AccountRepository accountRepository;
    private MongoTemplate mongoTemplate;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private AccountNotificationService notifications;
    private RegistrationService registrationService;
    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        notifications = mock(AccountNotificationService.class);
        registrationService = new RegistrationService(accountRepository, mongoTemplate, passwordEncoder, notifications);
        passwordService = new PasswordService(
                accountRepository, passwordEncoder, refreshTokenService, notifications, 900_000, 86_400_000);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RegisterRequest register(String email) {
        return new RegisterRequest(email, "password123", "password123", "Jane", "Doe", " Female ", null, "HR", true);
    }

    @Test
    void firstRegisteredAccountBecomesAdmin() {
        when(mongoTemplate.exists(any(Query.class), eq(Account.class))).thenReturn(false);

        registrationService.register(register("jane@example.com"), "http://localhost:4310");

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("Admin");
        assertThat(saved.getValue().getGender()).isEqualTo("Female");
        verify(notifications).sendVerificationEmail(eq("jane@example.com"), anyString(), eq("http://localhost:4310"));
    }

    @Test
    void laterAccountsAreUsers() {
        when(mongoTemplate.exists(any(Query.class), eq(Account.class))).thenReturn(true);

        registrationService.register(register("jane@example.com"), null);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo("User");
    }

    @Test
    void registeringExistingEmailSendsNoticeInsteadOfFailing() {
        when(accountRepository.existsByEmail("jane@example.com")).thenReturn(true);

        registrationService.register(register("Jane@Example.com"), "http://localhost:4310");

        verify(accountRepository, never()).save(any());
        verify(notifications).sendAlreadyRegisteredEmail("jane@example.com", "http://localhost:4310");
    }

    @Test
    void forgotPasswordForUnknownEmailSendsNothing() {
        when(accountRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        passwordService.forgotPassword("nobody@example.com", null);

        verify(notifications, never()).sendPasswordResetEmail(anyString(), anyString(), any());
    }

    @Test
    void forgotPasswordStoresOnlyTokenHash() {
        Account account = new Account();
        account.setEmail("jane@example.com");
        when(accountRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(account));

        passwordService.forgotPassword("jane@example.com", null);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendPasswordResetEmail(eq("jane@example.com"), token.capture(), any());
        assertThat(account.getResetToken().getToken()).isEqualTo(SecureTokens.sha256(token.getValue()));
    }

    @Test
    void resetPasswordLooksUpByHashMarksVerifiedAndRevokesSessions() {
        Account account = new Account();
        account.setId("64b000000000000000000001");
        Account.ResetToken resetToken = new Account.ResetToken();
        resetToken.setToken(SecureTokens.sha256("raw"));
        resetToken.setExpires(Instant.now().plusSeconds(60));
        account.setResetToken(resetToken);
        when(accountRepository.findByResetTokenToken(SecureTokens.sha256("raw"))).thenReturn(Optional.of(account));

        passwordService.resetPassword(new ResetPasswordRequest("raw", "password123", "password123"));

        assertThat(account.getResetToken()).isNull();
        assertThat(account.isVerified()).isTrue();
        assertThat(account.getPasswordHash()).isEqualTo("hashed");
        verify(refreshTokenService).revokeAllForAccount("64b000000000000000000001");
        verify(accountRepository, never()).findAll();
    }
}
