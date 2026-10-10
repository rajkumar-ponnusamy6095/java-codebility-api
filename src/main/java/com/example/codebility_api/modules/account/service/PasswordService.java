package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.ChangePasswordRequest;
import com.example.codebility_api.modules.account.dto.ResetPasswordRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import com.example.codebility_api.shared.security.SecureTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

/** Forgot/reset/change password and admin-created account password setup. */
@Service
public class PasswordService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AccountNotificationService notifications;
    private final long resetExpirationMs;
    private final long setupExpirationMs;

    public PasswordService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AccountNotificationService notifications,
            @Value("${app.password-reset.expiration-ms}") long resetExpirationMs,
            @Value("${app.password-setup.expiration-ms:86400000}") long setupExpirationMs) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.notifications = notifications;
        this.resetExpirationMs = resetExpirationMs;
        this.setupExpirationMs = setupExpirationMs;
    }

    /** Always succeeds so callers cannot tell whether the email is registered. */
    public void forgotPassword(String rawEmail, String origin) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        accountRepository.findByEmail(email).ifPresent(account -> {
            String token = issueResetToken(account, resetExpirationMs);
            accountRepository.save(account);
            notifications.sendPasswordResetEmail(email, token, origin);
        });
    }

    /** Sets a password-setup token on a new admin-created account; caller saves the account. */
    String issueSetupToken(Account account) {
        return issueResetToken(account, setupExpirationMs);
    }

    public void validateResetToken(String rawToken) {
        findByResetToken(rawToken);
    }

    public void resetPassword(ResetPasswordRequest request) {
        Account account = findByResetToken(request.token());
        Instant now = Instant.now();
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setPasswordReset(now);
        account.setResetToken(null);
        account.setUpdated(now);
        accountRepository.save(account);
        refreshTokenService.revokeAllForAccount(account.getId());
    }

    public void changePassword(AuthenticatedAccount caller, ChangePasswordRequest request) {
        Account account = accountRepository.findById(caller.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!passwordEncoder.matches(request.oldPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        account.setUpdated(Instant.now());
        accountRepository.save(account);
        refreshTokenService.revokeAllForAccount(account.getId());
    }

    private String issueResetToken(Account account, long expirationMs) {
        String rawToken = SecureTokens.newToken();
        Instant now = Instant.now();
        Account.ResetToken resetToken = new Account.ResetToken();
        resetToken.setToken(SecureTokens.sha256(rawToken));
        resetToken.setExpires(now.plusMillis(expirationMs));
        account.setResetToken(resetToken);
        account.setUpdated(now);
        return rawToken;
    }

    private Account findByResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        return accountRepository.findByResetTokenToken(SecureTokens.sha256(rawToken))
                .filter(PasswordService::hasUnexpiredResetToken)
                .orElseThrow(PasswordService::invalidToken);
    }

    private static boolean hasUnexpiredResetToken(Account account) {
        Account.ResetToken resetToken = account.getResetToken();
        return resetToken != null
                && resetToken.getExpires() != null
                && resetToken.getExpires().isAfter(Instant.now());
    }

    private static ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token");
    }
}
