
package com.example.codebility_api.controller;

import com.example.codebility_api.dto.RegisterRequest;
import com.example.codebility_api.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.codebility_api.dto.VerifyEmailRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.example.codebility_api.dto.LoginRequest;
import com.example.codebility_api.dto.LoginResponse;

import java.util.Map;

import com.example.codebility_api.dto.AuthenticationResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.codebility_api.dto.ChangePasswordRequest;
import com.example.codebility_api.dto.ForgotPasswordRequest;

import com.example.codebility_api.dto.ValidateResetTokenRequest;
import com.example.codebility_api.dto.ResetPasswordRequest;
import com.example.codebility_api.dto.AccountListResponse;

import com.example.codebility_api.dto.CreateAccountRequest;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.codebility_api.dto.UpdateAccountRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.example.codebility_api.dto.RevokeTokenRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.codebility_api.service.RefreshTokenService;
import com.example.codebility_api.dto.AccountPaginationResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.codebility_api.dto.AuthenticateResponse;
import com.example.codebility_api.dto.AccountProfileResponse;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final RefreshTokenService refreshTokenService;

    public AccountController(AccountService accountService,
      RefreshTokenService refreshTokenService) {
        this.accountService = accountService;
        this.refreshTokenService = refreshTokenService;

    }

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request) {

        accountService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "message",
                        "Registration successful. Please verify your email."
                ));
    }


    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        accountService.verifyEmail(request.token());

        return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully"
        ));
    }



    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticateResponse> authenticate(
            @Valid @RequestBody LoginRequest request) {

        AuthenticationResult result =
                accountService.authenticate(request);

        ResponseCookie refreshCookie = ResponseCookie
                .from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/accounts")
                .maxAge(Duration.ofDays(7))
                .build();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshCookie.toString()
                )
                .body(new AuthenticateResponse(
                        result.response().account().email(),
                        result.response().accessToken()
                ));
    }


    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponse> refreshToken(
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String rawToken) {

        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token cookie is missing"
            );
        }

        AuthenticationResult result =
                accountService.refreshAuthentication(rawToken);

        ResponseCookie refreshCookie = ResponseCookie
                .from("refreshToken", result.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/accounts")
                .maxAge(Duration.ofDays(7))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }


    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String rawToken) {

        accountService.logout(rawToken);

        ResponseCookie clearCookie = ResponseCookie
                .from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/accounts")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(Map.of("message", "Logout successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<AccountProfileResponse> getCurrentAccount(
            @AuthenticationPrincipal String email) {

        return ResponseEntity.ok(
                accountService.getCurrentAccount(email)
        );
    }


    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ChangePasswordRequest request) {

        accountService.changePassword(email, request);

        return ResponseEntity.ok(Map.of(
                "message",
                "Password changed successfully. Please log in again."
        ));
    }


    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        accountService.forgotPassword(request);

        return ResponseEntity.ok(Map.of(
                "message",
                "If an account exists for that email, password-reset instructions will be sent."
        ));
    }


    @PostMapping("/validate-reset-token")
    public ResponseEntity<Map<String, Object>> validateResetToken(
            @Valid @RequestBody ValidateResetTokenRequest request) {

        boolean valid = accountService.validateResetToken(request.token());

        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid or expired reset token"
            );
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Reset token is valid"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        accountService.resetPassword(request);

        return ResponseEntity.ok(Map.of(
                "message",
                "Password reset successfully. Please log in again."
        ));
    }


    @GetMapping
    public ResponseEntity<AccountPaginationResponse> getAllAccounts(
            @RequestParam(defaultValue = "page") String pagination,
            @RequestParam(required = false) String afterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department
    ) {
        return ResponseEntity.ok(
                accountService.getPaginatedAccounts(
                        pagination,
                        afterId,
                        page,
                        limit,
                        search,
                        sortBy,
                        sortOrder,
                        role,
                        status,
                        department
                )
        );
    }




    @PostMapping
    public ResponseEntity<LoginResponse.AccountDetails> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        LoginResponse.AccountDetails account =
                accountService.createAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }



    @GetMapping("/{id}")
    public ResponseEntity<LoginResponse.AccountDetails> getAccountById(
            @PathVariable String id) {

        return ResponseEntity.ok(
                accountService.getAccountById(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoginResponse.AccountDetails> updateAccount(
            @PathVariable String id,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        return ResponseEntity.ok(
                accountService.updateAccount(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable String id
    ) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/accounts")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }



    @PostMapping("/revoke-token")
    public ResponseEntity<Map<String, String>> revokeToken(
            @RequestBody(required = false) RevokeTokenRequest request,
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String refreshTokenCookie,
            HttpServletResponse response
    ) {
        String token = request != null
                && request.token() != null
                && !request.token().isBlank()
                ? request.token()
                : refreshTokenCookie;

        if (token != null && !token.isBlank()) {
            refreshTokenService.revokeByRawToken(token);
        }

        clearRefreshTokenCookie(response);

        return ResponseEntity.ok(
                Map.of("message", "Token revoked")
        );
    }



}

