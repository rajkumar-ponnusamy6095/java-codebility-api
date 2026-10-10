package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.AccountProfileResponse;
import com.example.codebility_api.modules.account.dto.CreateAccountRequest;
import com.example.codebility_api.modules.account.dto.UpdateAccountRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTests {

    private static final String USER_ID = "64b000000000000000000001";
    private static final String OTHER_ID = "64b000000000000000000003";
    private static final AuthenticatedAccount USER = new AuthenticatedAccount(USER_ID, "jane@example.com", "User");
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(OTHER_ID, "admin@example.com", "Admin");

    private AccountRepository accountRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private PasswordService passwordService;
    private AccountNotificationService notifications;
    private AccountService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        passwordService = mock(PasswordService.class);
        notifications = mock(AccountNotificationService.class);
        service = new AccountService(accountRepository, passwordEncoder, refreshTokenService, passwordService, notifications);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    }

    private static Account existing() {
        Account account = new Account();
        account.setId(USER_ID);
        account.setEmail("jane@example.com");
        account.setFirstName("Jane");
        account.setLastName("Doe");
        account.setGender("Female");
        account.setRole("User");
        account.setStatus("active");
        return account;
    }

    private static UpdateAccountRequest update(String gender, String role, String password) {
        return new UpdateAccountRequest(null, "  Janet ", "", gender, null, null, role, null, password, password);
    }

    @Test
    void partialUpdateKeepsOmittedFieldsAndTrimsGender() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(existing()));

        AccountProfileResponse response = service.updateAccount(USER_ID, update("  Other ", null, null), USER);

        assertThat(response.firstName()).isEqualTo("Janet");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.gender()).isEqualTo("Other");
        verify(refreshTokenService, never()).revokeAllForAccount(anyString());
    }

    @Test
    void userCannotUpdateAnotherAccount() {
        assertThatThrownBy(() -> service.updateAccount(OTHER_ID, update(null, null, null), USER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void userCannotChangeOwnRole() {
        assertThatThrownBy(() -> service.updateAccount(USER_ID, update(null, "Admin", null), USER))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void adminCanChangeRoleOfAnyAccount() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(existing()));

        assertThat(service.updateAccount(USER_ID, update(null, "Admin", null), ADMIN).role()).isEqualTo("Admin");
    }

    @Test
    void passwordUpdateRevokesRefreshTokens() {
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(existing()));

        service.updateAccount(USER_ID, update(null, null, "newpassword1"), USER);

        verify(refreshTokenService).revokeAllForAccount(USER_ID);
    }

    @Test
    void createAccountSendsSetupEmailAndReturnsProfileWithGender() {
        when(passwordService.issueSetupToken(any())).thenReturn("setup-token");
        CreateAccountRequest request = new CreateAccountRequest(
                " New@Example.com ", "New", "User", "  Male ", null, "HR", "User", null);

        AccountProfileResponse response = service.createAccount(request, "http://localhost:4310");

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.gender()).isEqualTo("Male");
        assertThat(response.status()).isEqualTo("active");
        verify(notifications).sendPasswordSetupEmail("new@example.com", "setup-token", "http://localhost:4310");
    }

    @Test
    void createAccountWithExistingEmailReturns409() {
        when(accountRepository.existsByEmail("new@example.com")).thenReturn(true);
        CreateAccountRequest request = new CreateAccountRequest(
                "new@example.com", "New", "User", "Male", null, null, "User", null);

        assertThatThrownBy(() -> service.createAccount(request, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(notifications, never()).sendPasswordSetupEmail(anyString(), anyString(), any());
    }

    @Test
    void deleteRemovesAccountAndItsRefreshTokens() {
        Account account = existing();
        when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(account));

        service.deleteAccount(USER_ID, USER);

        verify(accountRepository).delete(eq(account));
        verify(refreshTokenService).deleteAllForAccount(USER_ID);
    }
}
