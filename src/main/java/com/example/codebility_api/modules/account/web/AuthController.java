package com.example.codebility_api.modules.account.web;

import com.example.codebility_api.modules.account.dto.AuthenticateResponse;
import com.example.codebility_api.modules.account.dto.AuthenticationResult;
import com.example.codebility_api.modules.account.dto.ChangePasswordRequest;
import com.example.codebility_api.modules.account.dto.ForgotPasswordRequest;
import com.example.codebility_api.modules.account.dto.LoginRequest;
import com.example.codebility_api.modules.account.dto.MessageResponse;
import com.example.codebility_api.modules.account.dto.RegisterRequest;
import com.example.codebility_api.modules.account.dto.ResetPasswordRequest;
import com.example.codebility_api.modules.account.dto.RevokeTokenRequest;
import com.example.codebility_api.modules.account.dto.ValidateResetTokenRequest;
import com.example.codebility_api.modules.account.dto.VerifyEmailRequest;
import com.example.codebility_api.modules.account.service.AuthService;
import com.example.codebility_api.modules.account.service.PasswordService;
import com.example.codebility_api.modules.account.service.RegistrationService;
import com.example.codebility_api.shared.security.AuthenticatedAccount;
import com.example.codebility_api.shared.web.ClientIpResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/accounts")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordService passwordService;
    private final RefreshTokenCookies cookies;

    public AuthController(
            AuthService authService,
            RegistrationService registrationService,
            PasswordService passwordService,
            RefreshTokenCookies cookies) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordService = passwordService;
        this.cookies = cookies;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticateResponse> authenticate(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AuthenticationResult result = authService.authenticate(body, ClientIpResolver.resolve(request));
        return withRefreshCookie(request, result);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthenticateResponse> refreshToken(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String token,
            HttpServletRequest request) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
        AuthenticationResult result = authService.refresh(token, ClientIpResolver.resolve(request));
        return withRefreshCookie(request, result);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String token,
            HttpServletRequest request) {
        authService.logout(token, ClientIpResolver.resolve(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear(request))
                .body(new MessageResponse("Logged out successfully"));
    }

    @PostMapping("/revoke-token")
    public ResponseEntity<MessageResponse> revokeToken(
            @RequestBody(required = false) RevokeTokenRequest body,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String cookieToken,
            @AuthenticationPrincipal AuthenticatedAccount caller,
            HttpServletRequest request) {
        String token = body != null && body.token() != null && !body.token().isBlank()
                ? body.token()
                : cookieToken;
        authService.revoke(token, ClientIpResolver.resolve(request), caller);
        return ResponseEntity.ok(new MessageResponse("Token revoked"));
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        registrationService.register(body, request.getHeader(HttpHeaders.ORIGIN));
        return new MessageResponse(
                "Registration successful, please check your email for verification instructions");
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        registrationService.verifyEmail(body.token());
        return new MessageResponse("Verification successful, you can now login");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {
        passwordService.forgotPassword(body.email(), request.getHeader(HttpHeaders.ORIGIN));
        return new MessageResponse("Please check your email for password reset instructions");
    }

    @PostMapping("/validate-reset-token")
    public MessageResponse validateResetToken(@Valid @RequestBody ValidateResetTokenRequest body) {
        passwordService.validateResetToken(body.token());
        return new MessageResponse("Token is valid");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        passwordService.resetPassword(body);
        return new MessageResponse("Password reset successful, you can now login");
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(
            @AuthenticationPrincipal AuthenticatedAccount caller,
            @Valid @RequestBody ChangePasswordRequest body) {
        passwordService.changePassword(caller, body);
        return new MessageResponse("Password changed successfully");
    }

    private ResponseEntity<AuthenticateResponse> withRefreshCookie(
            HttpServletRequest request, AuthenticationResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(request, result.refreshToken()))
                .body(result.toResponse());
    }
}
