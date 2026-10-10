package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.dto.AccountProfileResponse;
import com.example.codebility_api.modules.account.dto.CreateAccountRequest;
import com.example.codebility_api.modules.account.dto.UpdateAccountRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import com.example.codebility_api.shared.security.SecureTokens;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

/**
 * Account CRUD. Users may read, update and delete their own account; Admins
 * may act on any account and are the only ones who can change role or status.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PasswordService passwordService;
    private final AccountNotificationService notifications;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            PasswordService passwordService,
            AccountNotificationService notifications) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.passwordService = passwordService;
        this.notifications = notifications;
    }

    public AccountProfileResponse getCurrentAccount(AuthenticatedAccount caller) {
        return AccountMapper.toProfile(load(caller.id()));
    }

    public AccountProfileResponse getAccountById(String id, AuthenticatedAccount caller) {
        requireAccess(id, caller);
        return AccountMapper.toProfile(load(id));
    }

    public AccountProfileResponse createAccount(CreateAccountRequest request, String origin) {
        String email = normalizeEmail(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw alreadyRegistered(email);
        }

        Instant now = Instant.now();
        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(SecureTokens.newToken()));
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setGender(request.gender().trim());
        account.setPhone(AccountFields.trimToNull(request.phone()));
        account.setDepartment(request.department());
        account.setRole(request.role());
        account.setStatus(request.status() == null ? "active" : request.status());
        account.setAcceptTerms(true);
        account.setCreated(now);
        account.setCreatedAt(now);
        account.setUpdated(now);
        String setupToken = passwordService.issueSetupToken(account);

        Account saved;
        try {
            saved = accountRepository.save(account);
        } catch (DuplicateKeyException ex) {
            throw alreadyRegistered(email);
        }
        notifications.sendPasswordSetupEmail(email, setupToken, origin);
        return AccountMapper.toProfile(saved);
    }

    public AccountProfileResponse updateAccount(
            String id, UpdateAccountRequest request, AuthenticatedAccount caller) {
        requireAccess(id, caller);
        if (!caller.isAdmin() && (hasText(request.role()) || hasText(request.status()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only admins can change role or status");
        }
        Account account = load(id);

        if (hasText(request.email())) {
            String email = normalizeEmail(request.email());
            if (!email.equals(account.getEmail()) && accountRepository.existsByEmail(email)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Email \"" + email + "\" is already taken");
            }
            account.setEmail(email);
        }
        if (hasText(request.firstName())) {
            account.setFirstName(request.firstName().trim());
        }
        if (hasText(request.lastName())) {
            account.setLastName(request.lastName().trim());
        }
        if (request.gender() != null) {
            account.setGender(request.gender().trim());
        }
        if (hasText(request.phone())) {
            account.setPhone(request.phone().trim());
        }
        if (hasText(request.department())) {
            account.setDepartment(request.department());
        }
        if (hasText(request.role())) {
            account.setRole(request.role());
        }
        if (hasText(request.status())) {
            account.setStatus(request.status());
        }
        boolean passwordChanged = hasText(request.password());
        if (passwordChanged) {
            account.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        account.setUpdated(Instant.now());

        Account saved;
        try {
            saved = accountRepository.save(account);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Email \"" + account.getEmail() + "\" is already taken");
        }
        if (passwordChanged) {
            refreshTokenService.revokeAllForAccount(id);
        }
        return AccountMapper.toProfile(saved);
    }

    public void deleteAccount(String id, AuthenticatedAccount caller) {
        requireAccess(id, caller);
        Account account = load(id);
        accountRepository.delete(account);
        refreshTokenService.deleteAllForAccount(id);
    }

    private Account load(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw notFound();
        }
        return accountRepository.findById(id).orElseThrow(AccountService::notFound);
    }

    private static void requireAccess(String id, AuthenticatedAccount caller) {
        if (!caller.canAccess(id)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You can only access your own account");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
    }

    private static ResponseStatusException alreadyRegistered(String email) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Email \"" + email + "\" is already registered");
    }
}
