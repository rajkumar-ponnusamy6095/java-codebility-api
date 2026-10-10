package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.domain.Role;
import com.example.codebility_api.modules.account.dto.RegisterRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.SecureTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

/**
 * Self-service sign-up and email verification. Registering an existing email
 * returns the same result as a new sign-up and emails the owner instead, so
 * the endpoint cannot be used to discover accounts.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AccountRepository accountRepository;
    private final MongoTemplate mongoTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AccountNotificationService notifications;

    public RegistrationService(
            AccountRepository accountRepository,
            MongoTemplate mongoTemplate,
            PasswordEncoder passwordEncoder,
            AccountNotificationService notifications) {
        this.accountRepository = accountRepository;
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
        this.notifications = notifications;
    }

    public void register(RegisterRequest request, String origin) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (accountRepository.existsByEmail(email)) {
            notifications.sendAlreadyRegisteredEmail(email, origin);
            return;
        }

        boolean firstAccount = !mongoTemplate.exists(new Query(), Account.class);
        Instant now = Instant.now();
        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setGender(request.gender().trim());
        account.setPhone(AccountFields.trimToNull(request.phone()));
        account.setDepartment(request.department());
        account.setAcceptTerms(request.acceptTerms());
        account.setRole(firstAccount ? Role.ADMIN : Role.USER);
        account.setStatus("active");
        account.setVerificationToken(SecureTokens.newToken());
        account.setCreated(now);
        account.setCreatedAt(now);
        account.setUpdated(now);

        try {
            accountRepository.save(account);
        } catch (DuplicateKeyException ex) {
            log.info("Concurrent registration for an existing email; sending already-registered email");
            notifications.sendAlreadyRegisteredEmail(email, origin);
            return;
        }
        notifications.sendVerificationEmail(email, account.getVerificationToken(), origin);
    }

    public void verifyEmail(String token) {
        Account account = accountRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification failed"));
        Instant now = Instant.now();
        account.setVerified(now);
        account.setVerificationToken(null);
        account.setUpdated(now);
        accountRepository.save(account);
    }
}
