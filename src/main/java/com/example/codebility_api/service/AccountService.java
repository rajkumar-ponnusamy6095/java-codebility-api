
package com.example.codebility_api.service;

import com.example.codebility_api.dto.RegisterRequest;
import com.example.codebility_api.entity.Account;
import com.example.codebility_api.repository.AccountRepository;
import com.example.codebility_api.service.EmailService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.example.codebility_api.dto.LoginRequest;
import com.example.codebility_api.dto.LoginResponse;
import com.example.codebility_api.dto.AuthenticationResult;
import com.example.codebility_api.entity.RefreshToken;
import com.example.codebility_api.dto.ChangePasswordRequest;

import com.example.codebility_api.dto.ForgotPasswordRequest;
import java.security.SecureRandom;
import java.util.Base64;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import com.example.codebility_api.dto.ResetPasswordRequest;
import com.example.codebility_api.dto.AccountListResponse;
import java.util.List;

import com.example.codebility_api.dto.CreateAccountRequest;
import com.example.codebility_api.dto.UpdateAccountRequest;

import com.example.codebility_api.dto.AccountPaginationResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.example.codebility_api.dto.AccountProfileResponse;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.regex.Pattern;


@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final MongoTemplate mongoTemplate;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JwtService jwtService,
            MongoTemplate mongoTemplate,
            RefreshTokenService refreshTokenService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.mongoTemplate = mongoTemplate;
        this.refreshTokenService = refreshTokenService;
    }

    public void register(RegisterRequest request) {
        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (accountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }

        Instant now = Instant.now();

        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setGender(request.gender().trim());
        account.setPhone(
                request.phone() == null
                        ? null
                        : request.phone().trim()
        );
        account.setDepartment(request.department());
        account.setAcceptTerms(request.acceptTerms());

        // These values are controlled by the server.
        account.setRole("User");
        account.setStatus("active");
        account.setVerified(null);
        account.setPasswordReset(null);

        // Used by the next step: email verification.
        account.setVerificationToken(
                UUID.randomUUID().toString()
        );

        account.setCreated(now);
        account.setCreatedAt(now);
        account.setUpdated(now);

        try {
            Account savedAccount = accountRepository.save(account);

            emailService.sendVerificationEmail(
                    savedAccount.getEmail(),
                    savedAccount.getVerificationToken()
            );
        } catch (DuplicateKeyException ex) {
            // Handles concurrent registrations for the same email.
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }
    }


    public void verifyEmail(String token) {
        Account account = accountRepository
                .findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or already-used verification token"
                ));

        account.setVerified(java.time.Instant.now());
        account.setVerificationToken(null);
        account.setUpdated(java.time.Instant.now());

        accountRepository.save(account);
    }


    public AuthenticationResult  authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase(
                java.util.Locale.ROOT
        );

        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"
                ));

        if (!passwordEncoder.matches(
                request.password(), account.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        if (!account.isVerified()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Please verify your email before logging in"
            );
        }

        if (!"active".equalsIgnoreCase(account.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is inactive"
            );
        }


        String accessToken = jwtService.generateToken(
                account.getEmail(),
                account.getRole()
        );

        String refreshToken = refreshTokenService.createToken(
                account.getId()
        );

        LoginResponse response = new LoginResponse(
                "Login successful",
                accessToken,
                "Bearer",
                900,
                toAccountDetails(account)
        );

        return new AuthenticationResult(response, refreshToken);

    }


    public AuthenticationResult refreshAuthentication(String rawToken) {
        // Validate the existing refresh token.
        RefreshToken oldToken =
                refreshTokenService.validateToken(rawToken);

        // Find the account associated with this token.
        Account account = accountRepository
                .findById(oldToken.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid refresh token"
                ));

        // Do not refresh sessions for unverified or inactive accounts.
        if (!account.isVerified()
                || !"active".equalsIgnoreCase(account.getStatus())) {
            refreshTokenService.revokeToken(oldToken);

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is unverified or inactive"
            );
        }

        // Revoke the old token before issuing its replacement.
        refreshTokenService.revokeToken(oldToken);

        String newRefreshToken =
                refreshTokenService.createToken(account.getId());

        String accessToken = jwtService.generateToken(
                account.getEmail(),
                account.getRole()
        );

        LoginResponse response = new LoginResponse(
                "Token refreshed successfully",
                accessToken,
                "Bearer",
                900,
                toAccountDetails(account)
        );

        return new AuthenticationResult(response, newRefreshToken);
    }


    public void logout(String rawToken) {
        refreshTokenService.revokeByRawToken(rawToken);
    }

    public AccountProfileResponse getCurrentAccount(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        return new AccountProfileResponse(
                account.getId(),
                account.getGender(),
                account.getFirstName(),
                account.getLastName(),
                account.getEmail(),
                account.getPhone(),
                account.getDepartment(),
                account.getStatus(),
                account.getRole(),
                account.getCreatedAt(),
                account.getUpdated(),
                account.isVerified()
        );
    }


    public void changePassword(
            String email,
            ChangePasswordRequest request) {

        Account account = accountRepository.findByEmail(
                email.trim().toLowerCase(java.util.Locale.ROOT)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Account not found"
        ));

        // Verify the current password.
        if (!passwordEncoder.matches(
                request.currentPassword(),
                account.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Current password is incorrect"
            );
        }

        // Prevent changing to the same password.
        if (passwordEncoder.matches(
                request.newPassword(),
                account.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must be different from current password"
            );
        }

        // Hash and save the new password.
        account.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );
        account.setUpdated(java.time.Instant.now());

        accountRepository.save(account);

        // Revoke all active refresh-token sessions.
        refreshTokenService.revokeAllForAccount(account.getId());
    }


    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim()
                .toLowerCase(java.util.Locale.ROOT);

        Account account = accountRepository.findByEmail(email)
                .orElse(null);

        // Use the same response for existing and unknown emails.
        // This prevents account enumeration.
        if (account == null) {
            return;
        }

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        String tokenHash = hashResetToken(rawToken);
        java.time.Instant now = java.time.Instant.now();

        Account.ResetToken resetToken = new Account.ResetToken();
        resetToken.setToken(tokenHash);
        resetToken.setExpires(now.plusMillis(900000));

        account.setResetToken(resetToken);
        account.setUpdated(now);

        accountRepository.save(account);

        emailService.sendPasswordResetEmail(email, rawToken);
    }

    private String hashResetToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }


    public boolean validateResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        String tokenHash = hashResetToken(rawToken);

        return accountRepository.findByResetTokenToken(tokenHash)
                .filter(this::hasUnexpiredResetToken)
                .isPresent();
    }


    private boolean hasUnexpiredResetToken(Account account) {
        Account.ResetToken resetToken = account.getResetToken();
        return resetToken != null
                && resetToken.getExpires() != null
                && resetToken.getExpires().isAfter(java.time.Instant.now());
    }

    public void resetPassword(ResetPasswordRequest request) {
        String rawToken = request.token();

        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or expired reset token"
            );
        }

        String tokenHash = hashResetToken(rawToken);

        Account account = accountRepository.findByResetTokenToken(tokenHash)
                .filter(this::hasUnexpiredResetToken)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired reset token"
                ));

        account.setPasswordHash(
                passwordEncoder.encode(request.newPassword())
        );
        account.setResetToken(null);
        account.setUpdated(java.time.Instant.now());

        accountRepository.save(account);

        refreshTokenService.revokeAllForAccount(account.getId());
    }


    public AccountListResponse getAllAccounts() {
        List<LoginResponse.AccountDetails> accounts =
                accountRepository.findAll().stream()
                        .map(this::toAccountDetails)
                        .toList();

        return new AccountListResponse(accounts, accounts.size());
    }


    public LoginResponse.AccountDetails createAccount(
            CreateAccountRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(java.util.Locale.ROOT);

        if (accountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists"
            );
        }

        java.time.Instant now = java.time.Instant.now();

        Account account = new Account();
        account.setEmail(email);
        account.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setGender(trimNullable(request.gender()));
        account.setPhone(request.phone());
        account.setDepartment(request.department());
        account.setRole(request.role());
        account.setStatus(
                request.status() == null
                        ? "active"
                        : request.status()
        );

        account.setAcceptTerms(true);
        account.setVerified(now);
        account.setVerificationToken(null);
        account.setResetToken(null);
        account.setCreated(now);
        account.setCreatedAt(now);
        account.setUpdated(now);

        Account saved = accountRepository.save(account);

        return toAccountDetails(saved);
    }


    public LoginResponse.AccountDetails getAccountById(String id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        return toAccountDetails(account);
    }

    public LoginResponse.AccountDetails updateAccount(
            String id,
            UpdateAccountRequest request
    ) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        String email = request.email().trim().toLowerCase();

        accountRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Email already exists"
                    );
                });

        account.setEmail(email);
        account.setFirstName(request.firstName().trim());
        account.setLastName(request.lastName().trim());
        account.setGender(trimNullable(request.gender()));
        account.setPhone(request.phone());
        account.setDepartment(request.department());
        account.setRole(request.role());
        account.setStatus(request.status());
        account.setUpdated(Instant.now());

        Account updatedAccount = accountRepository.save(account);

        return toAccountDetails(updatedAccount);
    }

    public void deleteAccount(String id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        refreshTokenService.revokeAllForAccount(account.getId());

        accountRepository.delete(account);
    }

    private LoginResponse.AccountDetails toAccountDetails(Account account) {
        return new LoginResponse.AccountDetails(
                account.getId(),
                account.getEmail(),
                account.getFirstName(),
                account.getLastName(),
                account.getGender(),
                account.getRole(),
                account.getStatus(),
                account.isVerified()
        );
    }

    public AccountPaginationResponse getPaginatedAccounts(
            String pagination,
            String afterId,
            int page,
            int requestedLimit,
            String search,
            String sortBy,
            String sortOrder,
            String role,
            String status,
            String department
    ) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));

        if (page < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be at least 1"
            );
        }

        String mode = pagination == null ? "page" : pagination;
        String sortField = sortBy == null || sortBy.isBlank()
                ? "id" : sortBy;
        String direction = sortOrder == null || sortOrder.isBlank()
                ? "asc" : sortOrder.toLowerCase();

        List<String> allowedSortFields = List.of(
                "id", "gender", "firstName", "lastName", "email",
                "phone", "department", "status", "role", "createdAt", "updated"
        );

        if (!allowedSortFields.contains(sortField)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported sortBy field"
            );
        }

        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sortOrder must be asc or desc"
            );
        }

        if (!mode.equals("page") && !mode.equals("cursor")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "pagination must be page or cursor"
            );
        }

        if (mode.equals("cursor")
                && (!sortField.equals("id") || !direction.equals("asc"))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cursor pagination requires sortBy=id and sortOrder=asc"
            );
        }

        List<Criteria> filters = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            String pattern = Pattern.quote(search.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i"),
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("phone").regex(pattern, "i")
            ));
        }

        if (role != null && !role.isBlank()) {
            filters.add(Criteria.where("role").is(role));
        }

        if (status != null && !status.isBlank()) {
            filters.add(Criteria.where("status").is(status));
        }

        if (department != null && !department.isBlank()) {
            filters.add(Criteria.where("department").is(department));
        }

        Criteria combinedCriteria = new Criteria();
        if (!filters.isEmpty()) {
            combinedCriteria.andOperator(filters.toArray(new Criteria[0]));
        }

        Query filterQuery = filters.isEmpty()
                ? new Query()
                : new Query(combinedCriteria);

        if (mode.equals("page")) {
            long total = mongoTemplate.count(
                    filterQuery,
                    Account.class
            );

            Sort.Direction sortDirection = direction.equals("asc")
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

            Query pageQuery = filterQuery
                    .with(PageRequest.of(
                            page - 1,
                            limit,
                            Sort.by(sortDirection, sortField)
                    ));

            List<AccountProfileResponse> data = mongoTemplate
                    .find(pageQuery, Account.class)
                    .stream()
                    .map(this::toAccountProfile)
                    .toList();

            int totalPages = (int) Math.ceil((double) total / limit);

            return new AccountPaginationResponse(
                    data,
                    new AccountPaginationResponse.PaginationMetadata(
                            page,
                            limit,
                            total,
                            totalPages,
                            null,
                            null
                    )
            );
        }

        if (afterId != null && !afterId.isBlank()
                && !ObjectId.isValid(afterId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid afterId"
            );
        }

        List<Criteria> cursorFilters = new ArrayList<>(filters);

        if (afterId != null && !afterId.isBlank()) {
            cursorFilters.add(
                    Criteria.where("id").gt(new ObjectId(afterId))
            );
        }

        Query cursorQuery;
        if (cursorFilters.isEmpty()) {
            cursorQuery = new Query();
        } else {
            cursorQuery = new Query(
                    new Criteria().andOperator(
                            cursorFilters.toArray(new Criteria[0])
                    )
            );
        }

        cursorQuery.with(Sort.by(Sort.Direction.ASC, "id"));
        cursorQuery.limit(limit + 1);

        List<Account> accounts = mongoTemplate.find(
                cursorQuery,
                Account.class
        );

        boolean hasMore = accounts.size() > limit;

        if (hasMore) {
            accounts = new ArrayList<>(accounts.subList(0, limit));
        }

        String nextCursor = hasMore && !accounts.isEmpty()
                ? accounts.get(accounts.size() - 1).getId()
                : null;

        List<AccountProfileResponse> data = accounts.stream()
                .map(this::toAccountProfile)
                .toList();

        return new AccountPaginationResponse(
                data,
                new AccountPaginationResponse.PaginationMetadata(
                        null,
                        limit,
                        null,
                        null,
                        hasMore,
                        nextCursor
                )
        );
    }

    private static String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private AccountProfileResponse toAccountProfile(Account account) {
        return new AccountProfileResponse(
                account.getId(),
                account.getGender(),
                account.getFirstName(),
                account.getLastName(),
                account.getEmail(),
                account.getPhone(),
                account.getDepartment(),
                account.getStatus(),
                account.getRole(),
                account.getCreatedAt(),
                account.getUpdated(),
                account.isVerified()
        );
    }


}
