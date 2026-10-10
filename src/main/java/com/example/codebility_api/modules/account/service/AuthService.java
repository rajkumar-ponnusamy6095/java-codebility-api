package com.example.codebility_api.modules.account.service;

import com.example.codebility_api.modules.account.domain.Account;
import com.example.codebility_api.modules.account.domain.RefreshToken;
import com.example.codebility_api.modules.account.dto.AuthenticationResult;
import com.example.codebility_api.modules.account.dto.LoginRequest;
import com.example.codebility_api.modules.account.repository.AccountRepository;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import com.example.codebility_api.shared.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** Login, refresh-token rotation, logout and token revocation. */
@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "Email or password is incorrect";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public AuthenticationResult authenticate(LoginRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByEmail(email)
                .filter(Account::isVerified)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS));
        requireActive(account);

        String refreshToken = refreshTokenService.createToken(account.getId(), ipAddress);
        return new AuthenticationResult(
                account.getEmail(),
                jwtService.generateToken(account.getId(), account.getRole()),
                refreshToken);
    }

    public AuthenticationResult refresh(String rawToken, String ipAddress) {
        RefreshToken current = refreshTokenService.findActive(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
        Account account = accountRepository.findById(current.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
        if (!"active".equalsIgnoreCase(account.getStatus())) {
            refreshTokenService.revoke(current, ipAddress);
            requireActive(account);
        }

        String replacement = refreshTokenService.rotate(current, ipAddress);
        return new AuthenticationResult(
                account.getEmail(),
                jwtService.generateToken(account.getId(), account.getRole()),
                replacement);
    }

    public void logout(String rawToken, String ipAddress) {
        refreshTokenService.findActive(rawToken)
                .ifPresent(token -> refreshTokenService.revoke(token, ipAddress));
    }

    public void revoke(String rawToken, String ipAddress, AuthenticatedAccount caller) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }
        RefreshToken token = refreshTokenService.findActive(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));
        if (!caller.canAccess(token.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only revoke your own tokens");
        }
        refreshTokenService.revoke(token, ipAddress);
    }

    private static void requireActive(Account account) {
        if (!"active".equalsIgnoreCase(account.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is inactive");
        }
    }
}
